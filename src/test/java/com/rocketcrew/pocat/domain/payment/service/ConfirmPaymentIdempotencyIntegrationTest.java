package com.rocketcrew.pocat.domain.payment.service;

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
import com.rocketcrew.pocat.domain.settlement.service.SettlementEventHandler;
import com.rocketcrew.pocat.domain.notification.service.NotificationEventHandler;
import com.rocketcrew.pocat.domain.order.entity.Order;
import com.rocketcrew.pocat.domain.order.enums.OrderStatus;
import com.rocketcrew.pocat.domain.order.enums.OrderType;
import com.rocketcrew.pocat.domain.order.repository.OrderRepository;
import com.rocketcrew.pocat.domain.payment.client.out.portone.PortOneClientService;
import com.rocketcrew.pocat.domain.payment.client.out.portone.PortOneStatus;
import com.rocketcrew.pocat.domain.payment.client.out.portone.dto.PortOnePaymentResponse;
import com.rocketcrew.pocat.domain.payment.entity.Payment;
import com.rocketcrew.pocat.domain.payment.entity.PaymentStatus;
import com.rocketcrew.pocat.domain.payment.entity.PaymentType;
import com.rocketcrew.pocat.domain.payment.repository.PaymentRepository;
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
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * confirmPayment 중복 완료 차단 통합 테스트
 *
 * Client Confirm 경로에서 동일 paymentUid로 동시 or 중복 호출 시
 * COMPLETED 결제가 1건만 생성되는지 검증한다.
 *
 * ─────────────────────────────────────────────────────────────
 * 이중 방어 구조
 * ─────────────────────────────────────────────────────────────
 * 1차 (락 없음): payment.isFinalized() 조기 반환
 * 2차 (락 있음): findPaymentByUidWithLock → payment.isFinalized() 재체크
 *   → 먼저 도착한 스레드가 completePayment() 실행 후 커밋
 *   → 이후 스레드는 락 획득 후 isFinalized=true → 조기 반환
 * ─────────────────────────────────────────────────────────────
 */
@Tag("concurrency")
@SpringBootTest
@Import(MockRedisTestConfig.class)
@DisplayName("confirmPayment 중복 완료 차단 통합 테스트")
class ConfirmPaymentIdempotencyIntegrationTest {

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
    @MockBean private SettlementEventHandler settlementEventHandler;
    @MockBean private NotificationEventHandler notificationEventHandler;

    // ── 실제 빈 ──────────────────────────────────────────────────
    @Autowired private PaymentApplicationService paymentApplicationService;
    @Autowired private PaymentRepository paymentRepository;
    @Autowired private OrderRepository orderRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private TransactionTemplate transactionTemplate;
    @Autowired private JdbcTemplate jdbcTemplate;

    private static final String TEST_EMAIL       = "it-confirm-buyer@test.com";
    private static final String TEST_ORDER_UID   = "IT-CONFIRM-ORDER-001";
    private static final String TEST_PAYMENT_UID = "IT-CONFIRM-PAY-001";

    private Long buyerId;
    private Long orderId;

    @BeforeEach
    void setUp() {
        PortOnePaymentResponse paidResponse = PortOnePaymentResponse.builder()
                .status(PortOneStatus.PAID)
                .amount(10_000L)
                .paymentMethod("card")
                .paidAt(LocalDateTime.now())
                .build();
        when(portOneClientService.getPayment(anyString())).thenReturn(paidResponse);

        transactionTemplate.execute(status -> {
            User buyer = userRepository.save(User.builder()
                    .email(TEST_EMAIL)
                    .password("encoded-pw")
                    .nickname("확정멱등테스트구매자")
                    .userRole(UserRole.USER)
                    .build());
            buyerId = buyer.getId();

            Order order = orderRepository.save(Order.builder()
                    .auctionId(300L)
                    .cardId(1L)
                    .sellerId(2L)
                    .buyerId(buyerId)
                    .orderUid(TEST_ORDER_UID)
                    .finalPrice(10_000L)
                    .status(OrderStatus.AUTO_PAYMENT_FAILED)
                    .orderType(OrderType.AUCTION)
                    .paymentDeadline(LocalDateTime.now().plusHours(1))
                    .build());
            orderId = order.getId();

            paymentRepository.save(Payment.builder()
                    .orderId(orderId)
                    .paymentUid(TEST_PAYMENT_UID)
                    .amount(10_000L)
                    .paymentType(PaymentType.PG_DIRECT)
                    .status(PaymentStatus.PENDING)
                    .build());
            return null;
        });
    }

    @AfterEach
    void tearDown() {
        jdbcTemplate.update("DELETE FROM payments WHERE order_id = ?", orderId);
        jdbcTemplate.update("DELETE FROM orders WHERE order_uid = ?", TEST_ORDER_UID);
        jdbcTemplate.update("DELETE FROM users WHERE email = ?", TEST_EMAIL);
    }

    @Nested
    @DisplayName("confirmPayment() 동시성")
    class ConcurrentConfirmPayment {

        @Test
        @DisplayName("동시 10스레드 호출 → COMPLETED 1건, 나머지는 isFinalized 조기 반환")
        void concurrent_confirmPayment_completesOnce() throws InterruptedException {
            int threadCount = 10;
            ExecutorService executor = Executors.newFixedThreadPool(threadCount);
            CountDownLatch startLatch = new CountDownLatch(1);
            CountDownLatch doneLatch  = new CountDownLatch(threadCount);

            AtomicInteger successCount = new AtomicInteger(0);
            AtomicInteger failCount    = new AtomicInteger(0);

            for (int i = 0; i < threadCount; i++) {
                executor.submit(() -> {
                    try {
                        startLatch.await();
                        paymentApplicationService.confirmPayment(buyerId, TEST_PAYMENT_UID);
                        successCount.incrementAndGet();
                    } catch (Exception e) {
                        failCount.incrementAndGet();
                    } finally {
                        doneLatch.countDown();
                    }
                });
            }

            startLatch.countDown();
            boolean completed = doneLatch.await(30, TimeUnit.SECONDS);
            executor.shutdown();

            assertThat(completed).as("모든 스레드가 제한 시간 내 완료되어야 함").isTrue();

            Long completedCount = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM payments WHERE order_id = ? AND status = 'COMPLETED'",
                    Long.class, orderId);

            System.out.println("=== confirmPayment 동시성 결과 ===");
            System.out.printf("성공: %d건 | 실패: %d건 | COMPLETED: %d건%n",
                    successCount.get(), failCount.get(), completedCount);

            // 비관적 락 + isFinalized 2중 방어로 COMPLETED는 1건만
            assertThat(completedCount)
                    .as("COMPLETED 결제는 1건만 생성되어야 함")
                    .isEqualTo(1L);
            assertThat(successCount.get())
                    .as("모든 스레드가 성공 또는 조기 반환해야 함 (예외 없음)")
                    .isEqualTo(threadCount);
        }
    }

    @Nested
    @DisplayName("confirmPayment() 멱등성")
    class ConfirmPaymentIdempotency {

        @Test
        @DisplayName("순차 2회 호출 → COMPLETED 1건, PortOne getPayment 1회만 호출")
        void sequential_confirmPayment_completesOnce() {
            paymentApplicationService.confirmPayment(buyerId, TEST_PAYMENT_UID);
            // 두 번째: 1차 isFinalized() 체크에서 조기 반환
            paymentApplicationService.confirmPayment(buyerId, TEST_PAYMENT_UID);

            Long completedCount = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM payments WHERE order_id = ? AND status = 'COMPLETED'",
                    Long.class, orderId);

            assertThat(completedCount).as("COMPLETED 결제는 1건만 존재해야 함").isEqualTo(1L);

            // 두 번째 호출은 isFinalized 조기 반환 → PortOne 추가 호출 없음
            verify(portOneClientService, times(1)).getPayment(TEST_PAYMENT_UID);
        }
    }

    @Nested
    @DisplayName("confirmPayment() 금액 불일치")
    class AmountMismatch {

        @Test
        @DisplayName("PortOne 금액 불일치 → cancelPayment 호출 후 PAYMENT_AMOUNT_MISMATCH 예외")
        void confirmPayment_amountMismatch_cancelsThenThrows() {
            // PortOne이 다른 금액을 반환하는 케이스
            PortOnePaymentResponse mismatchResponse = PortOnePaymentResponse.builder()
                    .status(PortOneStatus.PAID)
                    .amount(5_000L)   // DB 결제 금액(10,000)과 불일치
                    .paymentMethod("card")
                    .paidAt(LocalDateTime.now())
                    .build();
            when(portOneClientService.getPayment(anyString())).thenReturn(mismatchResponse);
            // cancelPayment → SUCCEEDED 반환 (status() NPE 방지)
            when(portOneClientService.cancelPayment(anyString(), anyLong(), anyString()))
                    .thenReturn(com.rocketcrew.pocat.domain.payment.client.out.portone.dto.PortOneCancelResponse.builder()
                            .status(com.rocketcrew.pocat.domain.payment.client.out.portone.PortOneCancelStatus.SUCCEEDED)
                            .build());

            org.junit.jupiter.api.Assertions.assertThrows(
                    com.rocketcrew.pocat.global.exception.domain.PaymentException.class,
                    () -> paymentApplicationService.confirmPayment(buyerId, TEST_PAYMENT_UID)
            );

            // 금액 불일치 → PortOne 취소 호출 확인
            verify(portOneClientService, times(1))
                    .cancelPayment(eq(TEST_PAYMENT_UID), anyLong(), anyString());

            // 결제 상태는 CANCELLED (취소 처리)
            String paymentStatus = jdbcTemplate.queryForObject(
                    "SELECT status FROM payments WHERE order_id = ?", String.class, orderId);
            // cancelPayment를 SUCCEEDED로 모킹했으므로 CANCELLED만 기대
            assertThat(paymentStatus)
                    .as("금액 불일치로 취소된 결제 상태는 CANCELLED여야 함")
                    .isEqualTo("CANCELLED");
        }
    }
}
