package com.rocketcrew.pocat.domain.settlement.service;

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

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 정산 멱등성 통합 테스트
 *
 * 실제 DB(H2)와 Spring 컨텍스트를 사용해 createSettlement() 멱등성을 검증한다.
 * 외부 의존성 (Redis, Redisson, PortOne, Kafka)은 @MockBean 으로 격리.
 *
 * ─────────────────────────────────────────────────────────────
 * 설계 의도
 * ─────────────────────────────────────────────────────────────
 * SettlementCommandService.createSettlement()는 두 단계로 중복 생성을 방지한다.
 *
 * 1차 방어 (순차 중복): existsByOrderId() 체크 — 이미 존재하면 즉시 반환
 * 2차 방어 (동시 레이스 컨디션): saveAndFlush()의 DataIntegrityViolationException 캐치
 *   → settlements.order_id unique 제약으로 두 번째 INSERT가 실패
 *   → 예외를 catch해 existsByOrderId()를 재확인 후 무시
 *
 * 최종 결과: 몇 번 호출하더라도 정산은 1건만 DB에 존재한다.
 * ─────────────────────────────────────────────────────────────
 */
@Tag("concurrency")
@SpringBootTest
@Import(MockRedisTestConfig.class)
@DisplayName("정산 멱등성 통합 테스트")
class SettlementIdempotencyIntegrationTest {

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
    @Autowired private SettlementCommandService settlementCommandService;
    @Autowired private OrderRepository orderRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private TransactionTemplate transactionTemplate;
    @Autowired private JdbcTemplate jdbcTemplate;

    private static final String TEST_EMAIL     = "it-settlement-buyer@test.com";
    private static final String TEST_ORDER_UID = "IT-SETTLEMENT-ORDER-001";

    private Long buyerId;
    private Long orderId;

    // ── 공통 세팅 ─────────────────────────────────────────────────

    @BeforeEach
    void setUp() {
        transactionTemplate.execute(status -> {
            User buyer = userRepository.save(User.builder()
                    .email(TEST_EMAIL)
                    .password("encoded-pw")
                    .nickname("정산테스트구매자")
                    .userRole(UserRole.USER)
                    .build());
            buyerId = buyer.getId();

            Order order = orderRepository.save(Order.builder()
                    .auctionId(777L)
                    .cardId(1L)
                    .sellerId(2L)
                    .buyerId(buyerId)
                    .orderUid(TEST_ORDER_UID)
                    .finalPrice(10_000L)
                    .status(OrderStatus.PAYMENT_COMPLETED)
                    .orderType(OrderType.AUCTION)
                    .build());
            orderId = order.getId();
            return null;
        });
    }

    @AfterEach
    void tearDown() {
        // @SQLDelete(소프트 딜리트) 우회 — 실제 행 삭제
        jdbcTemplate.update("DELETE FROM settlements WHERE order_id = ?", orderId);
        jdbcTemplate.update("DELETE FROM orders WHERE order_uid = ?", TEST_ORDER_UID);
        jdbcTemplate.update("DELETE FROM users WHERE email = ?", TEST_EMAIL);
    }

    // ── 테스트 ────────────────────────────────────────────────────

    @Nested
    @DisplayName("createSettlement() 멱등성")
    class SettlementIdempotency {

        @Test
        @DisplayName("순차 중복 호출: 두 번 호출해도 정산 1건만 생성됨 (existsByOrderId 1차 방어)")
        void sequential_createSettlement_createsOnlyOne() {
            settlementCommandService.createSettlement(TEST_ORDER_UID);
            settlementCommandService.createSettlement(TEST_ORDER_UID); // 두 번째 호출 — 조기 반환

            Long count = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM settlements WHERE order_id = ?",
                    Long.class, orderId);

            assertThat(count).as("순차 중복 호출 시 정산은 1건만 생성되어야 함").isEqualTo(1L);
        }

        @Test
        @DisplayName("동시 중복 호출: 10개 스레드가 같은 orderUid로 요청해도 정산 1건만 생성됨")
        void concurrent_createSettlement_createsOnlyOne() throws InterruptedException {
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
                        settlementCommandService.createSettlement(TEST_ORDER_UID);
                        successCount.incrementAndGet();
                    } catch (Exception e) {
                        // DataIntegrityViolationException 래핑된 예외 등
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

            // 성공/실패 여부와 무관하게 DB에는 정산 1건만 존재해야 함
            Long count = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM settlements WHERE order_id = ?",
                    Long.class, orderId);

            System.out.println("=== 정산 멱등성 결과 ===");
            System.out.printf("성공: %d건 | 실패: %d건 | 생성된 정산: %d건%n",
                    successCount.get(), failCount.get(), count);

            assertThat(count)
                    .as("동시 중복 호출 시 정산은 1건만 생성되어야 함 (unique 제약 + DataIntegrityViolationException 처리)")
                    .isEqualTo(1L);
        }
    }
}
