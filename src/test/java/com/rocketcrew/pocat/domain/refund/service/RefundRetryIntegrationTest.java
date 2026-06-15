package com.rocketcrew.pocat.domain.refund.service;

import com.rocketcrew.pocat.cache.MockRedisTestConfig;
import com.rocketcrew.pocat.domain.auction.repository.AuctionSearchRepository;
import com.rocketcrew.pocat.domain.card.repository.CardSearchRepository;
import com.rocketcrew.pocat.domain.auction.service.AuctionEsIndexService;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import com.rocketcrew.pocat.domain.auction.kafka.AuctionEventHandler;
import com.rocketcrew.pocat.domain.bid.service.BidEventHandler;
import com.rocketcrew.pocat.domain.order.service.OrderEventHandler;
import com.rocketcrew.pocat.domain.payment.client.out.kafka.handler.PaymentEventHandler;
import com.rocketcrew.pocat.domain.refund.service.RefundEventHandler;
import com.rocketcrew.pocat.domain.notification.service.NotificationEventHandler;
import com.rocketcrew.pocat.domain.order.entity.Order;
import com.rocketcrew.pocat.domain.order.enums.OrderStatus;
import com.rocketcrew.pocat.domain.order.enums.OrderType;
import com.rocketcrew.pocat.domain.order.repository.OrderRepository;
import com.rocketcrew.pocat.domain.payment.client.out.portone.PortOneClientService;
import com.rocketcrew.pocat.domain.payment.entity.Payment;
import com.rocketcrew.pocat.domain.payment.entity.PaymentStatus;
import com.rocketcrew.pocat.domain.payment.entity.PaymentType;
import com.rocketcrew.pocat.domain.payment.repository.PaymentRepository;
import com.rocketcrew.pocat.domain.refund.entity.Refund;
import com.rocketcrew.pocat.domain.refund.entity.RefundStatus;
import com.rocketcrew.pocat.domain.refund.repository.RefundRepository;
import com.rocketcrew.pocat.domain.settlement.entity.Settlement;
import com.rocketcrew.pocat.domain.settlement.enums.SettlementStatus;
import com.rocketcrew.pocat.domain.settlement.repository.SettlementRepository;
import com.rocketcrew.pocat.domain.user.entity.User;
import com.rocketcrew.pocat.domain.user.enums.UserRole;
import com.rocketcrew.pocat.domain.user.repository.UserRepository;
import com.rocketcrew.pocat.global.outbox.service.OutboxEventWriter;
import com.rocketcrew.pocat.global.ratelimit.RedisRateLimiter;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.Tag;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 환불 재시도 통합 테스트
 *
 * retryRefund()의 PortOne 성공·실패·한도 초과 시나리오를 검증한다.
 * 지수 백오프 정책(RefundRetryPolicy)과 비관적 락 직렬화 효과도 함께 검증한다.
 *
 * ─────────────────────────────────────────────────────────────
 * 재시도 정책 (RefundRetryPolicy.MAX_RETRY_COUNT = 5)
 * ─────────────────────────────────────────────────────────────
 * retryCount < 5  → FAILED_RETRYABLE (지수 백오프 nextRetryAt 갱신)
 * retryCount >= 5 → FAILED_FINAL     (수동 처리 필요)
 * ─────────────────────────────────────────────────────────────
 */
@Tag("concurrency")
@SpringBootTest
@Import(MockRedisTestConfig.class)
@DisplayName("환불 재시도 통합 테스트")
class RefundRetryIntegrationTest {

    // ── 외부 의존성 Mock ─────────────────────────────────────────
    @MockBean private StringRedisTemplate stringRedisTemplate;
    @MockBean private RedissonClient redissonClient;
    @MockBean private RedisRateLimiter redisRateLimiter;
    @MockBean private PortOneClientService portOneClientService;
    @MockBean private OutboxEventWriter outboxEventWriter;
    @MockBean private AuctionSearchRepository auctionSearchRepository;
    @MockBean private CardSearchRepository cardSearchRepository;
    @MockBean private RedisConnectionFactory redisConnectionFactory;
    @MockBean private RedisMessageListenerContainer redisMessageListenerContainer;
    @MockBean private AuctionEsIndexService auctionEsIndexService;
    @MockBean private AuctionEventHandler auctionEventHandler;
    @MockBean private BidEventHandler bidEventHandler;
    @MockBean private OrderEventHandler orderEventHandler;
    @MockBean private PaymentEventHandler paymentEventHandler;
    @MockBean private RefundEventHandler refundEventHandler;
    @MockBean private NotificationEventHandler notificationEventHandler;

    // ── 실제 빈 ──────────────────────────────────────────────────
    @Autowired private RefundCommandService refundCommandService;
    @Autowired private RefundRepository refundRepository;
    @Autowired private SettlementRepository settlementRepository;
    @Autowired private PaymentRepository paymentRepository;
    @Autowired private OrderRepository orderRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private TransactionTemplate transactionTemplate;
    @Autowired private JdbcTemplate jdbcTemplate;

    private static final String TEST_EMAIL         = "it-refund-retry-buyer@test.com";
    private static final String TEST_ORDER_UID     = "IT-REFUND-RETRY-ORDER-001";
    private static final String TEST_PAYMENT_UID   = "IT-REFUND-RETRY-PAY-001";
    private static final String TEST_SETTLEMENT_UID = "IT-REFUND-RETRY-SETTLE-001";

    private Long buyerId;
    private Long orderId;
    private Long paymentId;

    @BeforeEach
    void setUp() {
        transactionTemplate.execute(status -> {
            User buyer = userRepository.save(User.builder()
                    .email(TEST_EMAIL)
                    .password("encoded-pw")
                    .nickname("환불재시도테스트구매자")
                    .userRole(UserRole.USER)
                    .build());
            buyerId = buyer.getId();

            Order order = orderRepository.save(Order.builder()
                    .auctionId(400L)
                    .cardId(1L)
                    .sellerId(2L)
                    .buyerId(buyerId)
                    .orderUid(TEST_ORDER_UID)
                    .finalPrice(10_000L)
                    .status(OrderStatus.PAYMENT_COMPLETED)
                    .orderType(OrderType.AUCTION)
                    .build());
            orderId = order.getId();

            Payment payment = paymentRepository.save(Payment.builder()
                    .orderId(orderId)
                    .paymentUid(TEST_PAYMENT_UID)
                    .amount(10_000L)
                    .paymentType(PaymentType.BILLING_KEY)
                    .status(PaymentStatus.COMPLETED)
                    .build());
            paymentId = payment.getId();

            settlementRepository.save(Settlement.builder()
                    .settlementUid(TEST_SETTLEMENT_UID)
                    .orderId(orderId)
                    .sellerId(2L)
                    .totalPrice(10_000L)
                    .platformFee(1_000L)
                    .sellerAmount(9_000L)
                    .status(SettlementStatus.PENDING)
                    .build());
            return null;
        });
    }

    @AfterEach
    void tearDown() {
        // @SQLDelete 우회 — JdbcTemplate 하드 DELETE
        jdbcTemplate.update("DELETE FROM refunds WHERE order_id = ?", orderId);
        jdbcTemplate.update("DELETE FROM settlements WHERE order_id = ?", orderId);
        jdbcTemplate.update("DELETE FROM payments WHERE order_id = ?", orderId);
        jdbcTemplate.update("DELETE FROM orders WHERE order_uid = ?", TEST_ORDER_UID);
        jdbcTemplate.update("DELETE FROM users WHERE email = ?", TEST_EMAIL);
    }

    // ── 각 테스트에서 Refund를 직접 생성 (retryCount, nextRetryAt 제어) ──

    private Long createRefund(int retryCount, LocalDateTime nextRetryAt) {
        return transactionTemplate.execute(status -> {
            Refund refund = refundRepository.save(Refund.builder()
                    .orderId(orderId)
                    .paymentId(paymentId)
                    .amount(10_000L)
                    .reason("구매 취소 테스트")
                    .status(RefundStatus.FAILED_RETRYABLE)
                    .retryCount(retryCount)
                    .nextRetryAt(nextRetryAt)
                    .build());
            return refund.getId();
        });
    }

    @Nested
    @DisplayName("retryRefund() 재시도 결과")
    class RetryRefundResult {

        @Test
        @DisplayName("PortOne 취소 성공 → Refund COMPLETED, Payment REFUNDED, Order REFUNDED")
        void retryRefund_portoneSuccess_completesRefund() {
            Long refundId = createRefund(0, LocalDateTime.now().minusMinutes(5));

            // cancelPayment 기본 반환값(null) → 예외 없음 = 성공
            refundCommandService.retryRefund(refundId);

            String refundStatus = jdbcTemplate.queryForObject(
                    "SELECT status FROM refunds WHERE id = ?", String.class, refundId);
            String paymentStatus = jdbcTemplate.queryForObject(
                    "SELECT status FROM payments WHERE id = ?", String.class, paymentId);
            String orderStatus = jdbcTemplate.queryForObject(
                    "SELECT status FROM orders WHERE id = ?", String.class, orderId);

            assertThat(refundStatus).as("환불 성공 → COMPLETED").isEqualTo("COMPLETED");
            assertThat(paymentStatus).as("결제 환불 → REFUNDED").isEqualTo("REFUNDED");
            assertThat(orderStatus).as("주문 환불 → REFUNDED").isEqualTo("REFUNDED");
        }

        @Test
        @DisplayName("PortOne 취소 실패 (retryCount < 5) → FAILED_RETRYABLE, retryCount 증가")
        void retryRefund_portoneFail_incrementsRetryCount() {
            Long refundId = createRefund(1, LocalDateTime.now().minusMinutes(5));

            doThrow(new RuntimeException("PortOne 취소 실패"))
                    .when(portOneClientService).cancelPayment(anyString(), anyLong(), anyString());

            refundCommandService.retryRefund(refundId);

            String refundStatus = jdbcTemplate.queryForObject(
                    "SELECT status FROM refunds WHERE id = ?", String.class, refundId);
            Integer retryCount = jdbcTemplate.queryForObject(
                    "SELECT retry_count FROM refunds WHERE id = ?", Integer.class, refundId);

            assertThat(refundStatus).as("재시도 실패 → FAILED_RETRYABLE 유지").isEqualTo("FAILED_RETRYABLE");
            assertThat(retryCount).as("retryCount 증가").isEqualTo(2);
        }

        @Test
        @DisplayName("PortOne 취소 실패 (retryCount = 5) → FAILED_FINAL (수동 처리 필요)")
        void retryRefund_exhausted_marksFinalFailure() {
            Long refundId = createRefund(5, LocalDateTime.now().minusMinutes(5));

            doThrow(new RuntimeException("PortOne 취소 실패"))
                    .when(portOneClientService).cancelPayment(anyString(), anyLong(), anyString());

            refundCommandService.retryRefund(refundId);

            String refundStatus = jdbcTemplate.queryForObject(
                    "SELECT status FROM refunds WHERE id = ?", String.class, refundId);

            assertThat(refundStatus)
                    .as("재시도 한도(5회) 초과 → FAILED_FINAL")
                    .isEqualTo("FAILED_FINAL");
        }

        @Test
        @DisplayName("nextRetryAt 미도래 → 재시도 스킵 (상태 변화 없음)")
        void retryRefund_notDue_skips() {
            Long refundId = createRefund(1, LocalDateTime.now().plusMinutes(30));

            refundCommandService.retryRefund(refundId);

            String refundStatus = jdbcTemplate.queryForObject(
                    "SELECT status FROM refunds WHERE id = ?", String.class, refundId);
            Integer retryCount = jdbcTemplate.queryForObject(
                    "SELECT retry_count FROM refunds WHERE id = ?", Integer.class, refundId);

            assertThat(refundStatus).as("아직 재시도 시간 아님 → FAILED_RETRYABLE 유지").isEqualTo("FAILED_RETRYABLE");
            assertThat(retryCount).as("retryCount 변화 없음").isEqualTo(1);

            // PortOne 미호출
            verify(portOneClientService, never()).cancelPayment(anyString(), anyLong(), anyString());
        }
    }

    @Nested
    @DisplayName("retryRefund() 동시성")
    class ConcurrentRetryRefund {

        @Test
        @DisplayName("동시 10스레드 retryRefund → Order 비관적 락으로 직렬화, cancelPayment 1회만 호출")
        void concurrent_retryRefund_cancelPaymentCalledOnce() throws InterruptedException {
            Long refundId = createRefund(0, LocalDateTime.now().minusMinutes(5));

            int threadCount = 10;
            ExecutorService executor = Executors.newFixedThreadPool(threadCount);
            CountDownLatch startLatch = new CountDownLatch(1);
            CountDownLatch doneLatch  = new CountDownLatch(threadCount);

            for (int i = 0; i < threadCount; i++) {
                executor.submit(() -> {
                    try {
                        startLatch.await();
                        refundCommandService.retryRefund(refundId);
                    } catch (Exception ignored) {
                        // PESSIMISTIC_WRITE 락 대기 중 예외는 무시
                    } finally {
                        doneLatch.countDown();
                    }
                });
            }

            startLatch.countDown();
            boolean completed = doneLatch.await(30, TimeUnit.SECONDS);
            executor.shutdown();

            assertThat(completed).as("모든 스레드가 제한 시간 내 완료되어야 함").isTrue();

            // 비관적 락 직렬화: 먼저 처리한 스레드가 COMPLETED로 변경 → 나머지는 상태 체크 후 early return
            String refundStatus = jdbcTemplate.queryForObject(
                    "SELECT status FROM refunds WHERE id = ?", String.class, refundId);

            assertThat(refundStatus)
                    .as("환불 재시도 후 최종 상태는 COMPLETED")
                    .isEqualTo("COMPLETED");

            // cancelPayment는 정확히 1회만 호출
            verify(portOneClientService, times(1))
                    .cancelPayment(anyString(), anyLong(), anyString());
        }
    }
}
