package com.rocketcrew.pocat.domain.payment.service;

import com.rocketcrew.pocat.cache.MockRedisTestConfig;
import com.rocketcrew.pocat.domain.order.entity.Order;
import com.rocketcrew.pocat.domain.order.enums.DeliveryStatus;
import com.rocketcrew.pocat.domain.order.enums.OrderStatus;
import com.rocketcrew.pocat.domain.order.enums.OrderType;
import com.rocketcrew.pocat.domain.order.repository.OrderRepository;
import com.rocketcrew.pocat.domain.payment.client.out.portone.PortOneClientService;
import com.rocketcrew.pocat.domain.payment.dto.request.CreatePaymentRequest;
import com.rocketcrew.pocat.domain.user.entity.User;
import com.rocketcrew.pocat.domain.user.enums.UserRole;
import com.rocketcrew.pocat.domain.user.repository.UserRepository;
import com.rocketcrew.pocat.global.outbox.service.OutboxEventWriter;
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
import com.rocketcrew.pocat.global.ratelimit.RedisRateLimiter;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * 결제 동시성 통합 테스트
 *
 * 실제 DB(H2)와 Spring 컨텍스트를 사용해 generatePayment() 동시성 동작을 검증한다.
 * 외부 의존성 (Redis, Redisson, PortOne, Kafka)은 @MockBean 으로 격리.
 *
 * ─────────────────────────────────────────────────────────────
 * 설계 의도
 * ─────────────────────────────────────────────────────────────
 * generatePayment()는 동시 호출 시 PENDING 결제를 여러 건 생성할 수 있다.
 * 이는 의도된 동작이며, 중복 완료 차단은 confirmPayment() 단계에서
 * Order 비관적 락 + completePayment()의 상태 전이 검증으로 보장한다.
 * (Order가 이미 PAYMENT_COMPLETED 이면 ORDER_CANNOT_COMPLETE_PAYMENT 예외)
 * ─────────────────────────────────────────────────────────────
 */
@Tag("concurrency")
@SpringBootTest
@Import(MockRedisTestConfig.class)
@DisplayName("결제 동시성 통합 테스트")
class PaymentConcurrencyIntegrationTest {

    // ── 외부 의존성 Mock ─────────────────────────────────────────
    @MockBean private StringRedisTemplate stringRedisTemplate;
    @MockBean private RedissonClient redissonClient;
    @MockBean private RedisRateLimiter redisRateLimiter;
    @MockBean private PortOneClientService portOneClientService;
    @MockBean private OutboxEventWriter outboxEventWriter;
    // Elasticsearch 레포는 시작 시 인덱스 연결을 시도하므로 Mock으로 격리
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
    @Autowired private OrderRepository orderRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private TransactionTemplate transactionTemplate;
    @Autowired private JdbcTemplate jdbcTemplate;

    private static final String TEST_EMAIL    = "concurrency-test-buyer@test.com";
    private static final String TEST_ORDER_UID = "IT-CONCURRENCY-ORDER-001";

    private Long buyerId;
    private Long orderId;

    // ── 공통 세팅 ─────────────────────────────────────────────────

    @BeforeEach
    void setUp() {
        // 레이트리밋 통과 허용
        when(redisRateLimiter.isAllowed(anyString(), anyInt(), anyLong())).thenReturn(true);

        // 트랜잭션 커밋이 필요하므로 TransactionTemplate 사용
        transactionTemplate.execute(status -> {
            User buyer = userRepository.save(User.builder()
                    .email(TEST_EMAIL)
                    .password("encoded-pw")
                    .nickname("동시성테스트구매자")
                    .userRole(UserRole.USER)
                    .build());
            buyerId = buyer.getId();

            Order order = orderRepository.save(Order.builder()
                    .auctionId(999L)
                    .cardId(1L)
                    .sellerId(2L)
                    .buyerId(buyerId)
                    .orderUid(TEST_ORDER_UID)
                    .finalPrice(10000L)
                    .status(OrderStatus.AUTO_PAYMENT_FAILED)
                    .deliveryStatus(DeliveryStatus.PREPARING)
                    .orderType(OrderType.AUCTION)
                    .paymentDeadline(LocalDateTime.now().plusHours(1))
                    .build());
            orderId = order.getId();
            return null;
        });
    }

    @AfterEach
    void tearDown() {
        // @SQLDelete(소프트 딜리트)를 우회하여 실제 행을 삭제해야 다음 테스트 @BeforeEach에서 unique constraint 충돌이 없음
        if (orderId != null) {
            jdbcTemplate.update("DELETE FROM payments WHERE order_id = ?", orderId);
        }
        jdbcTemplate.update("DELETE FROM orders WHERE order_uid = ?", TEST_ORDER_UID);
        jdbcTemplate.update("DELETE FROM users WHERE email = ?", TEST_EMAIL);
    }

    // ── 테스트 ────────────────────────────────────────────────────

    @Nested
    @DisplayName("generatePayment() 동시성")
    class ConcurrentGeneratePayment {

        @Test
        @DisplayName("동시 요청 시 PENDING 여러 건 생성 — 중복 완료 차단은 confirmPayment에서 보장")
        void concurrent_generatePayment_createsMultiplePending() throws InterruptedException {
            int threadCount = 10;
            ExecutorService executor = Executors.newFixedThreadPool(threadCount);
            CountDownLatch startLatch = new CountDownLatch(1);   // 동시 출발 신호
            CountDownLatch doneLatch  = new CountDownLatch(threadCount);

            AtomicInteger successCount = new AtomicInteger(0);
            AtomicInteger failCount    = new AtomicInteger(0);

            CreatePaymentRequest request = new CreatePaymentRequest(orderId);

            for (int i = 0; i < threadCount; i++) {
                executor.submit(() -> {
                    try {
                        startLatch.await();   // 모든 스레드 준비 완료 대기
                        paymentApplicationService.generatePayment(buyerId, request);
                        successCount.incrementAndGet();
                    } catch (Exception e) {
                        failCount.incrementAndGet();
                    } finally {
                        doneLatch.countDown();
                    }
                });
            }

            startLatch.countDown();                        // 전 스레드 동시 출발
            boolean completed = doneLatch.await(15, TimeUnit.SECONDS);
            executor.shutdown();

            assertThat(completed).as("모든 스레드가 제한 시간 내 완료되어야 함").isTrue();

            Long pendingCount = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM payments WHERE order_id = ? AND status = 'PENDING'",
                    Long.class, orderId);

            System.out.println("=== 결제 동시성 결과 ===");
            System.out.printf("성공: %d건 | 실패: %d건 | PENDING 생성: %d건%n",
                    successCount.get(), failCount.get(), pendingCount);

            // generatePayment()는 동시 요청 시 PENDING 여러 건을 허용한다.
            // 중복 완료 차단은 confirmPayment()의 Order 상태 전이 검증으로 보장된다.
            assertThat(successCount.get())
                    .as("동시 요청 시 여러 스레드가 PENDING 결제를 생성할 수 있음")
                    .isGreaterThan(0);
            assertThat(pendingCount)
                    .as("성공한 요청마다 PENDING 결제 1건씩 생성됨")
                    .isEqualTo((long) successCount.get());
        }

        @Test
        @DisplayName("✅ 정상: 단건 요청 → PENDING 1건 생성")
        void singleRequest_createsSinglePending() {
            CreatePaymentRequest request = new CreatePaymentRequest(orderId);

            paymentApplicationService.generatePayment(buyerId, request);

            Long pendingCount = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM payments WHERE order_id = ? AND status = 'PENDING'",
                    Long.class, orderId);

            assertThat(pendingCount).isEqualTo(1L);
        }

        @Test
        @DisplayName("실패: 결제 기한 초과 주문 → PAYMENT_WINDOW_EXPIRED")
        void expiredOrder_throwsPaymentWindowExpired() {
            // paymentDeadline 과거인 새 주문
            Long expiredOrderId = transactionTemplate.execute(status -> {
                Order expired = orderRepository.save(Order.builder()
                        .auctionId(999L)
                        .cardId(1L)
                        .sellerId(2L)
                        .buyerId(buyerId)
                        .orderUid("IT-EXPIRED-ORDER-001")
                        .finalPrice(10000L)
                        .status(OrderStatus.AUTO_PAYMENT_FAILED)
                        .deliveryStatus(DeliveryStatus.PREPARING)
                        .orderType(OrderType.AUCTION)
                        .paymentDeadline(LocalDateTime.now().minusHours(2))  // 이미 만료
                        .build());
                return expired.getId();
            });

            org.junit.jupiter.api.Assertions.assertThrows(
                    com.rocketcrew.pocat.global.exception.domain.PaymentException.class,
                    () -> paymentApplicationService.generatePayment(buyerId, new CreatePaymentRequest(expiredOrderId))
            );

            // 정리 — @SQLDelete 우회하여 하드 딜리트
            jdbcTemplate.update("DELETE FROM payments WHERE order_id = ?", expiredOrderId);
            jdbcTemplate.update("DELETE FROM orders WHERE order_uid = ?", "IT-EXPIRED-ORDER-001");
        }
    }
}
