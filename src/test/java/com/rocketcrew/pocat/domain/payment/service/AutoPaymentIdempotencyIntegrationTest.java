package com.rocketcrew.pocat.domain.payment.service;

import com.rocketcrew.pocat.cache.MockRedisTestConfig;
import com.rocketcrew.pocat.domain.auction.repository.AuctionSearchRepository;
import com.rocketcrew.pocat.domain.auction.service.AuctionEsIndexService;
import com.rocketcrew.pocat.domain.card.repository.CardSearchRepository;
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
import com.rocketcrew.pocat.domain.payment.client.out.portone.PortOneStatus;
import com.rocketcrew.pocat.domain.payment.client.out.portone.dto.PortOnePaymentResponse;
import com.rocketcrew.pocat.domain.payment.dto.request.CreatePaymentRequest;
import com.rocketcrew.pocat.domain.payment.entity.Payment;
import com.rocketcrew.pocat.domain.payment.entity.PaymentStatus;
import com.rocketcrew.pocat.domain.payment.entity.PaymentType;
import com.rocketcrew.pocat.domain.payment.repository.PaymentRepository;
import com.rocketcrew.pocat.domain.user.entity.User;
import com.rocketcrew.pocat.domain.user.enums.UserRole;
import com.rocketcrew.pocat.domain.user.repository.UserRepository;
import com.rocketcrew.pocat.global.exception.domain.PaymentException;
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
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * autoPayment() 멱등성 · 동시성 통합 테스트
 *
 * 실제 DB(H2)와 Spring 컨텍스트를 사용해 autoPayment()의 중복 호출 안전성을 검증한다.
 * 외부 의존성 (Redis, Redisson, PortOne, Kafka)은 @MockBean 으로 격리.
 *
 * ─────────────────────────────────────────────────────────────
 * 설계 의도
 * ─────────────────────────────────────────────────────────────
 * autoPayment()는 Kafka consumer에서 호출되므로 네트워크 단절·재전송 등으로
 * 동일 메시지가 중복 소비될 수 있다. 이에 두 단계 멱등성 보호가 적용된다.
 *
 * 1차 (결제 레코드): createBillingKeyPaymentIfAbsent() — REQUIRES_NEW + Order 비관적 락
 *   → 동시 호출 시 BILLING_KEY 결제 레코드는 1건만 생성
 *
 * 2차 (PortOne 호출): markBillingKeyRequested() — REQUIRES_NEW + Payment 비관적 락
 *   → billingKeyRequestedAt 필드로 attemptBillingKeyPayment() 중복 호출 차단
 *   → 이미 요청된 경우 getPayment()로 상태 조회만 수행
 *
 * 3차 (결제 완료): completePayment() — REQUIRES_NEW + Payment·Order 비관적 락
 *   → isFinalized() 체크로 중복 완료 처리 차단
 * ─────────────────────────────────────────────────────────────
 */
@Tag("concurrency")
@SpringBootTest
@Import(MockRedisTestConfig.class)
@DisplayName("autoPayment 멱등성 · 동시성 통합 테스트")
class AutoPaymentIdempotencyIntegrationTest {

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
    @Autowired private PaymentApplicationService paymentApplicationService;
    @Autowired private PaymentRepository paymentRepository;
    @Autowired private OrderRepository orderRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private TransactionTemplate transactionTemplate;
    @Autowired private JdbcTemplate jdbcTemplate;

    private static final String TEST_EMAIL     = "it-autopay-buyer@test.com";
    private static final String TEST_ORDER_UID = "IT-AUTOPAY-ORDER-001";
    private static final String TEST_BILLING_KEY = "test-billing-key-auto";

    private Long buyerId;
    private Long orderId;

    // ── 공통 세팅 ─────────────────────────────────────────────────

    @BeforeEach
    void setUp() {
        // PortOne mock: 항상 PAID 반환
        PortOnePaymentResponse paidResponse = PortOnePaymentResponse.builder()
                .status(PortOneStatus.PAID)
                .amount(10_000L)
                .paymentMethod("card")
                .paidAt(LocalDateTime.now())
                .build();
        when(portOneClientService.attemptBillingKeyPayment(anyString(), anyString(), anyLong()))
                .thenReturn(paidResponse);
        when(portOneClientService.getPayment(anyString()))
                .thenReturn(paidResponse);

        transactionTemplate.execute(status -> {
            User buyer = userRepository.save(User.builder()
                    .email(TEST_EMAIL)
                    .password("encoded-pw")
                    .nickname("자동결제테스트구매자")
                    .userRole(UserRole.USER)
                    .billingKey(TEST_BILLING_KEY)
                    .build());
            buyerId = buyer.getId();

            Order order = orderRepository.save(Order.builder()
                    .auctionId(123L)
                    .cardId(1L)
                    .sellerId(2L)
                    .buyerId(buyerId)
                    .orderUid(TEST_ORDER_UID)
                    .finalPrice(10_000L)
                    .status(OrderStatus.PAYMENT_PENDING)
                    .orderType(OrderType.AUCTION)
                    .build());
            orderId = order.getId();
            return null;
        });
    }

    @AfterEach
    void tearDown() {
        // @SQLDelete(소프트 딜리트) 우회 — 실제 행 삭제
        jdbcTemplate.update("DELETE FROM payments WHERE order_id = ?", orderId);
        jdbcTemplate.update("DELETE FROM orders WHERE order_uid = ?", TEST_ORDER_UID);
        jdbcTemplate.update("DELETE FROM users WHERE email = ?", TEST_EMAIL);
    }

    // ── 테스트 ────────────────────────────────────────────────────

    @Nested
    @DisplayName("autoPayment() 동시성")
    class ConcurrentAutoPayment {

        @Test
        @DisplayName("동시 호출 시 BILLING_KEY 결제 1건만 생성되고 COMPLETED 상태로 완료됨")
        void concurrent_autoPayment_createsOnlyOneBillingKeyPaymentAndCompletes() throws InterruptedException {
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
                        paymentApplicationService.autoPayment(TEST_ORDER_UID);
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

            // Payment 엔티티에는 @SQLRestriction 없으므로 JdbcTemplate으로 직접 조회
            Long billingKeyCount = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM payments WHERE order_id = ? AND payment_type = 'BILLING_KEY'",
                    Long.class, orderId);

            List<String> statuses = jdbcTemplate.queryForList(
                    "SELECT status FROM payments WHERE order_id = ? AND payment_type = 'BILLING_KEY'",
                    String.class, orderId);

            System.out.println("=== autoPayment 동시성 결과 ===");
            System.out.printf("성공: %d건 | 실패: %d건 | BILLING_KEY 결제: %d건 | 상태: %s%n",
                    successCount.get(), failCount.get(), billingKeyCount, statuses);

            // 1차 멱등성: BILLING_KEY 결제 레코드 1건만 생성
            assertThat(billingKeyCount)
                    .as("createBillingKeyPaymentIfAbsent — BILLING_KEY 결제는 1건만 생성되어야 함")
                    .isEqualTo(1L);

            // 3차 멱등성: 최종 결제 상태는 COMPLETED
            assertThat(statuses).hasSize(1);
            assertThat(statuses.get(0))
                    .as("결제가 정상 완료 상태여야 함")
                    .isEqualTo("COMPLETED");

            // 2차 멱등성: PortOne attemptBillingKeyPayment는 정확히 1번만 호출
            verify(portOneClientService, times(1))
                    .attemptBillingKeyPayment(anyString(), eq(TEST_BILLING_KEY), eq(10_000L));
        }
    }

    @Nested
    @DisplayName("autoPayment() 멱등성")
    class AutoPaymentIdempotency {

        @Test
        @DisplayName("이미 완료된 주문에 재호출 시 새 결제 생성 없이 기존 결제 반환")
        void autoPayment_onCompletedOrder_returnsExistingPaymentWithoutNewCreation() {
            // 이미 결제 완료된 주문 준비
            Long completedOrderId = transactionTemplate.execute(status -> {
                Order order = orderRepository.save(Order.builder()
                        .auctionId(124L)
                        .cardId(1L)
                        .sellerId(2L)
                        .buyerId(buyerId)
                        .orderUid("IT-AUTOPAY-DONE-ORDER")
                        .finalPrice(10_000L)
                        .status(OrderStatus.PAYMENT_COMPLETED)
                        .orderType(OrderType.AUCTION)
                        .build());

                paymentRepository.save(Payment.builder()
                        .orderId(order.getId())
                        .paymentUid("IT-AUTOPAY-DONE-PAY")
                        .amount(10_000L)
                        .paymentType(PaymentType.BILLING_KEY)
                        .status(PaymentStatus.COMPLETED)
                        .build());

                return order.getId();
            });

            // 두 번 호출 — 두 번째도 새 결제를 만들지 않아야 함
            paymentApplicationService.autoPayment("IT-AUTOPAY-DONE-ORDER");
            paymentApplicationService.autoPayment("IT-AUTOPAY-DONE-ORDER");

            Long paymentCount = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM payments WHERE order_id = ?",
                    Long.class, completedOrderId);

            assertThat(paymentCount).as("결제는 기존 1건만 존재해야 함").isEqualTo(1L);
            // PAYMENT_COMPLETED 조기 반환 경로 — PortOne 호출 없어야 함
            verify(portOneClientService, never()).attemptBillingKeyPayment(anyString(), anyString(), anyLong());
            verify(portOneClientService, never()).getPayment(anyString());

            // 정리
            jdbcTemplate.update("DELETE FROM payments WHERE order_id = ?", completedOrderId);
            jdbcTemplate.update("DELETE FROM orders WHERE order_uid = ?", "IT-AUTOPAY-DONE-ORDER");
        }

        @Test
        @DisplayName("순차 중복 호출 시 BILLING_KEY 결제 1건만 생성됨 (createBillingKeyPaymentIfAbsent 1차 방어)")
        void sequential_autoPayment_createsOnlyOneBillingKeyPayment() {
            paymentApplicationService.autoPayment(TEST_ORDER_UID);
            paymentApplicationService.autoPayment(TEST_ORDER_UID); // 두 번째 호출

            Long billingKeyCount = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM payments WHERE order_id = ? AND payment_type = 'BILLING_KEY'",
                    Long.class, orderId);

            assertThat(billingKeyCount).as("BILLING_KEY 결제는 1건만 존재해야 함").isEqualTo(1L);

            // 두 번째 호출은 PAYMENT_COMPLETED 조기 반환 → PortOne 추가 호출 없음
            verify(portOneClientService, times(1))
                    .attemptBillingKeyPayment(anyString(), anyString(), anyLong());
        }
    }

    @Nested
    @DisplayName("generatePayment() 신규 검증")
    class GeneratePaymentValidation {

        @Test
        @DisplayName("즉시구매(BUYOUT) 주문은 직접결제 불가 → PAYMENT_BUYOUT_DIRECT_NOT_ALLOWED")
        void generatePayment_withBuyoutOrder_throwsBuyoutDirectNotAllowed() {
            Long buyoutOrderId = transactionTemplate.execute(status -> {
                Order order = orderRepository.save(Order.builder()
                        .auctionId(125L)
                        .cardId(1L)
                        .sellerId(2L)
                        .buyerId(buyerId)
                        .orderUid("IT-BUYOUT-ORDER-001")
                        .finalPrice(10_000L)
                        .status(OrderStatus.AUTO_PAYMENT_FAILED)
                        .orderType(OrderType.BUYOUT)
                        .paymentDeadline(LocalDateTime.now().plusHours(1))
                        .build());
                return order.getId();
            });

            assertThrows(PaymentException.class,
                    () -> paymentApplicationService.generatePayment(buyerId, new CreatePaymentRequest(buyoutOrderId)));

            // 직접결제 레코드가 생성되지 않아야 함
            Long paymentCount = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM payments WHERE order_id = ?",
                    Long.class, buyoutOrderId);
            assertThat(paymentCount).isZero();

            // 정리
            jdbcTemplate.update("DELETE FROM orders WHERE order_uid = ?", "IT-BUYOUT-ORDER-001");
        }
    }
}
