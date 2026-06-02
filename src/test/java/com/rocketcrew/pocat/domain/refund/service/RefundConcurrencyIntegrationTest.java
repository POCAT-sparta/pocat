package com.rocketcrew.pocat.domain.refund.service;

import com.rocketcrew.pocat.cache.MockRedisTestConfig;
import com.rocketcrew.pocat.domain.auction.repository.AuctionSearchRepository;
import com.rocketcrew.pocat.domain.card.repository.CardSearchRepository;
import com.rocketcrew.pocat.domain.order.entity.Order;
import com.rocketcrew.pocat.domain.order.enums.DeliveryStatus;
import com.rocketcrew.pocat.domain.order.enums.OrderStatus;
import com.rocketcrew.pocat.domain.order.enums.OrderType;
import com.rocketcrew.pocat.domain.order.repository.OrderRepository;
import com.rocketcrew.pocat.domain.payment.client.out.portone.PortOneClientService;
import com.rocketcrew.pocat.domain.payment.entity.Payment;
import com.rocketcrew.pocat.domain.payment.entity.PaymentStatus;
import com.rocketcrew.pocat.domain.payment.entity.PaymentType;
import com.rocketcrew.pocat.domain.payment.repository.PaymentRepository;
import com.rocketcrew.pocat.domain.refund.dto.request.CreateRefundRequest;
import com.rocketcrew.pocat.domain.refund.entity.RefundStatus;
import com.rocketcrew.pocat.domain.refund.repository.RefundRepository;
import com.rocketcrew.pocat.domain.user.entity.User;
import com.rocketcrew.pocat.domain.user.enums.UserRole;
import com.rocketcrew.pocat.domain.user.repository.UserRepository;
import com.rocketcrew.pocat.global.outbox.service.OutboxEventWriter;
import com.rocketcrew.pocat.global.ratelimit.RedisRateLimiter;
import org.junit.jupiter.api.*;
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

/**
 * 환불 동시성 통합 테스트
 *
 * 실제 DB(H2)와 Spring 컨텍스트를 사용해 createRefund() 동시 호출 동작을 검증한다.
 * 외부 의존성 (Redis, Redisson, PortOne, Kafka)은 @MockBean 으로 격리.
 *
 * ─────────────────────────────────────────────────────────────
 * 설계 의도
 * ─────────────────────────────────────────────────────────────
 * RefundCommandService.createRefund()는 Order에 비관적 락(PESSIMISTIC_WRITE)을
 * 걸어 동시 환불 요청을 직렬화한다.
 *
 * 직렬화 흐름:
 *   Thread 1: Order 락 획득 → existsByOrderIdAndStatusIn() = false → 환불 생성 → 커밋
 *   Thread 2: Order 락 대기 → (Thread 1 커밋 후) 락 획득 → existsByOrderIdAndStatusIn() = true
 *             → REFUND_ALREADY_EXISTS 예외
 *
 * 최종 결과: 동시 요청이 아무리 많아도 환불은 1건만 DB에 존재한다.
 * ─────────────────────────────────────────────────────────────
 */
@SpringBootTest
@Import(MockRedisTestConfig.class)
@DisplayName("환불 동시성 통합 테스트")
class RefundConcurrencyIntegrationTest {

    // ── 외부 의존성 Mock ─────────────────────────────────────────
    @MockBean private StringRedisTemplate stringRedisTemplate;
    @MockBean private RedissonClient redissonClient;
    @MockBean private RedisRateLimiter redisRateLimiter;
    @MockBean private PortOneClientService portOneClientService;
    @MockBean private OutboxEventWriter outboxEventWriter;
    @MockBean private AuctionSearchRepository auctionSearchRepository;
    @MockBean private CardSearchRepository cardSearchRepository;

    // ── 실제 빈 ──────────────────────────────────────────────────
    @Autowired private RefundCommandService refundCommandService;
    @Autowired private RefundRepository refundRepository;
    @Autowired private OrderRepository orderRepository;
    @Autowired private PaymentRepository paymentRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private TransactionTemplate transactionTemplate;
    @Autowired private JdbcTemplate jdbcTemplate;

    private static final String TEST_EMAIL     = "it-refund-buyer@test.com";
    private static final String TEST_ORDER_UID = "IT-REFUND-ORDER-001";
    private static final String TEST_PAYMENT_UID = "IT-REFUND-PAYMENT-001";

    private Long buyerId;
    private Long orderId;

    // ── 공통 세팅 ─────────────────────────────────────────────────

    @BeforeEach
    void setUp() {
        transactionTemplate.execute(status -> {
            User buyer = userRepository.save(User.builder()
                    .email(TEST_EMAIL)
                    .password("encoded-pw")
                    .nickname("환불테스트구매자")
                    .userRole(UserRole.USER)
                    .build());
            buyerId = buyer.getId();

            Order order = orderRepository.save(Order.builder()
                    .auctionId(555L)
                    .cardId(1L)
                    .sellerId(2L)
                    .buyerId(buyerId)
                    .orderUid(TEST_ORDER_UID)
                    .finalPrice(10_000L)
                    .status(OrderStatus.PAYMENT_COMPLETED)
                    .deliveryStatus(DeliveryStatus.PREPARING)
                    .orderType(OrderType.AUCTION)
                    .build());
            orderId = order.getId();

            // createRefund()는 COMPLETED 상태의 결제를 요구함
            paymentRepository.save(Payment.builder()
                    .orderId(orderId)
                    .paymentUid(TEST_PAYMENT_UID)
                    .amount(10_000L)
                    .paymentType(PaymentType.PG_DIRECT)
                    .status(PaymentStatus.COMPLETED)
                    .build());

            return null;
        });
    }

    @AfterEach
    void tearDown() {
        // @SQLDelete(소프트 딜리트) 우회 — 실제 행 삭제
        jdbcTemplate.update("DELETE FROM refunds WHERE order_id = ?", orderId);
        jdbcTemplate.update("DELETE FROM payments WHERE order_id = ?", orderId);
        jdbcTemplate.update("DELETE FROM orders WHERE order_uid = ?", TEST_ORDER_UID);
        jdbcTemplate.update("DELETE FROM users WHERE email = ?", TEST_EMAIL);
    }

    // ── 테스트 ────────────────────────────────────────────────────

    @Nested
    @DisplayName("createRefund() 동시성")
    class ConcurrentCreateRefund {

        @Test
        @DisplayName("동시 환불 요청: Order 비관적 락으로 직렬화 → 환불 1건만 생성, 나머지는 REFUND_ALREADY_EXISTS")
        void concurrent_createRefund_onlyOneRefundCreated() throws InterruptedException {
            int threadCount = 10;
            ExecutorService executor = Executors.newFixedThreadPool(threadCount);
            CountDownLatch startLatch = new CountDownLatch(1);
            CountDownLatch doneLatch  = new CountDownLatch(threadCount);

            AtomicInteger successCount = new AtomicInteger(0);
            AtomicInteger failCount    = new AtomicInteger(0);

            CreateRefundRequest request = new CreateRefundRequest(orderId, "단순 변심");

            for (int i = 0; i < threadCount; i++) {
                executor.submit(() -> {
                    try {
                        startLatch.await();
                        refundCommandService.createRefund(buyerId, request);
                        successCount.incrementAndGet();
                    } catch (Exception e) {
                        failCount.incrementAndGet();
                    } finally {
                        doneLatch.countDown();
                    }
                });
            }

            startLatch.countDown();
            boolean completed = doneLatch.await(15, TimeUnit.SECONDS);
            executor.shutdown();

            assertThat(completed).as("모든 스레드가 제한 시간 내 완료되어야 함").isTrue();

            // DB에서 직접 조회 — 소프트 딜리트 필터 미적용
            Long refundCount = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM refunds WHERE order_id = ?",
                    Long.class, orderId);

            System.out.println("=== 환불 동시성 결과 ===");
            System.out.printf("성공: %d건 | 실패: %d건 | 생성된 환불: %d건%n",
                    successCount.get(), failCount.get(), refundCount);

            // 환불은 정확히 1건만 생성되어야 함
            assertThat(refundCount)
                    .as("Order 비관적 락 직렬화로 환불은 1건만 생성되어야 함")
                    .isEqualTo(1L);
            assertThat(successCount.get())
                    .as("1건만 성공해야 함")
                    .isEqualTo(1);

            // 생성된 환불의 상태 검증
            List<Long> refundIds = jdbcTemplate.queryForList(
                    "SELECT id FROM refunds WHERE order_id = ?", Long.class, orderId);
            assertThat(refundIds).hasSize(1);

            refundRepository.findById(refundIds.get(0)).ifPresent(refund ->
                    assertThat(refund.getStatus()).isEqualTo(RefundStatus.REQUESTED)
            );
        }
    }
}
