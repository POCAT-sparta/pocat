package com.rocketcrew.pocat.domain.auction.service;

import com.rocketcrew.pocat.cache.MockRedisTestConfig;
import com.rocketcrew.pocat.domain.auction.entity.Auction;
import com.rocketcrew.pocat.domain.auction.enums.AuctionStatus;
import com.rocketcrew.pocat.domain.auction.repository.AuctionRepository;
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
import com.rocketcrew.pocat.domain.payment.client.out.portone.PortOneClientService;
import com.rocketcrew.pocat.domain.payment.client.out.portone.PortOneStatus;
import com.rocketcrew.pocat.domain.payment.client.out.portone.dto.PortOnePaymentResponse;
import com.rocketcrew.pocat.domain.user.entity.User;
import com.rocketcrew.pocat.domain.user.enums.UserRole;
import com.rocketcrew.pocat.domain.user.repository.UserRepository;
import com.rocketcrew.pocat.global.outbox.service.OutboxEventWriter;
import com.rocketcrew.pocat.global.ratelimit.RedisRateLimiter;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.Tag;
import org.mockito.Mockito;
import org.redisson.api.RLock;
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
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 즉시구매 동시성 통합 테스트
 *
 * 실제 DB(H2)와 Spring 컨텍스트를 사용해 buyout() 동시 호출 동작을 검증한다.
 * 외부 의존성 (Redis, Redisson, PortOne, Kafka)은 @MockBean 으로 격리.
 *
 * ─────────────────────────────────────────────────────────────
 * 동시성 보호 메커니즘
 * ─────────────────────────────────────────────────────────────
 * AuctionBuyoutService.buyout()
 *   └─ reserveBuyoutWithLock()
 *        └─ redissonClient.getLock("auction:lock:{auctionId}")
 *             └─ lock.tryLock(0, SECONDS)  ← 대기 없이 즉시 실패
 *                  성공: reserveBuyout (ACTIVE→PAYMENT_PENDING) → 주문·결제 처리 → ENDED
 *                  실패: AUCTION_LOCK_FAILED 즉시 반환
 * ─────────────────────────────────────────────────────────────
 */
@Tag("concurrency")
@SpringBootTest
@Import(MockRedisTestConfig.class)
@DisplayName("즉시구매 동시성 통합 테스트")
class AuctionBuyoutConcurrencyIntegrationTest {

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
    @Autowired private AuctionBuyoutService auctionBuyoutService;
    @Autowired private AuctionRepository auctionRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private TransactionTemplate transactionTemplate;
    @Autowired private JdbcTemplate jdbcTemplate;

    private static final String SELLER_EMAIL = "it-buyout-seller@test.com";
    private static final String BUYER_EMAIL  = "it-buyout-buyer@test.com";
    private static final String BILLING_KEY  = "buyout-test-billing-key";

    private Long sellerId;
    private Long buyerId;
    private Long auctionId;

    @BeforeEach
    void setUp() throws Exception {
        // Redisson 분산 락 → AtomicBoolean으로 시뮬레이션
        // unlock() 호출 후에도 false로 되돌리지 않아 "1회 성공 후 영구 실패"를 보장한다.
        // 이렇게 해야 성공 스레드가 커밋·해제한 뒤 대기 중이던 다른 스레드가
        // 우연히 락을 재획득하여 두 번째 buyout이 실행되는 경우를 방지한다.
        AtomicBoolean lockAcquired = new AtomicBoolean(false);
        RLock mockLock = Mockito.mock(RLock.class);
        when(redissonClient.getLock(anyString())).thenReturn(mockLock);
        doAnswer(inv -> lockAcquired.compareAndSet(false, true))
                .when(mockLock).tryLock(anyLong(), any(TimeUnit.class));
        when(mockLock.isHeldByCurrentThread()).thenReturn(true);
        doAnswer(inv -> null).when(mockLock).unlock(); // sticky: 한번 획득 후 해제해도 재획득 불가

        // PortOne mock: buyoutPrice(50,000원) PAID 응답
        PortOnePaymentResponse paidResponse = PortOnePaymentResponse.builder()
                .status(PortOneStatus.PAID)
                .amount(50_000L)
                .paymentMethod("card")
                .paidAt(LocalDateTime.now())
                .build();
        when(portOneClientService.attemptBillingKeyPayment(anyString(), anyString(), anyLong()))
                .thenReturn(paidResponse);
        when(portOneClientService.getPayment(anyString()))
                .thenReturn(paidResponse);

        transactionTemplate.execute(status -> {
            User seller = userRepository.save(User.builder()
                    .email(SELLER_EMAIL)
                    .password("encoded-pw")
                    .nickname("즉시구매판매자")
                    .userRole(UserRole.USER)
                    .build());
            sellerId = seller.getId();

            User buyer = userRepository.save(User.builder()
                    .email(BUYER_EMAIL)
                    .password("encoded-pw")
                    .nickname("즉시구매구매자")
                    .userRole(UserRole.USER)
                    .billingKey(BILLING_KEY)
                    .build());
            buyerId = buyer.getId();

            Auction auction = auctionRepository.save(Auction.builder()
                    .cardId(1L)
                    .sellerId(sellerId)
                    .title("즉시구매 동시성 테스트 경매")
                    .description("테스트용")
                    .startingPrice(10_000L)
                    .buyoutPrice(50_000L)
                    .status(AuctionStatus.ACTIVE)
                    .startedAt(LocalDateTime.now().minusHours(1))
                    .endedAt(LocalDateTime.now().plusDays(3))
                    .build());
            auctionId = auction.getId();
            return null;
        });
    }

    @AfterEach
    void tearDown() {
        // @SQLDelete 우회 — JdbcTemplate 하드 DELETE
        List<Long> orderIds = jdbcTemplate.queryForList(
                "SELECT id FROM orders WHERE auction_id = ?", Long.class, auctionId);
        orderIds.forEach(oid -> jdbcTemplate.update("DELETE FROM payments WHERE order_id = ?", oid));
        jdbcTemplate.update("DELETE FROM auction_bids WHERE auction_id = ?", auctionId);
        jdbcTemplate.update("DELETE FROM orders WHERE auction_id = ?", auctionId);
        jdbcTemplate.update("DELETE FROM auctions WHERE id = ?", auctionId);
        jdbcTemplate.update("DELETE FROM users WHERE email IN (?, ?)", SELLER_EMAIL, BUYER_EMAIL);
    }

    @Nested
    @DisplayName("buyout() 동시성")
    class ConcurrentBuyout {

        @Test
        @DisplayName("동시 10스레드 즉시구매 → Redisson 락으로 1건만 성공, 나머지는 AUCTION_LOCK_FAILED")
        void concurrent_buyout_onlyOneSucceeds() throws InterruptedException {
            int threadCount = 10;
            ExecutorService executor = Executors.newFixedThreadPool(threadCount);
            CountDownLatch startLatch = new CountDownLatch(1);
            CountDownLatch doneLatch  = new CountDownLatch(threadCount);

            AtomicInteger successCount   = new AtomicInteger(0);
            AtomicInteger lockFailCount  = new AtomicInteger(0);
            AtomicInteger otherFailCount = new AtomicInteger(0);

            for (int i = 0; i < threadCount; i++) {
                executor.submit(() -> {
                    try {
                        startLatch.await();
                        auctionBuyoutService.buyout(buyerId, auctionId);
                        successCount.incrementAndGet();
                    } catch (com.rocketcrew.pocat.global.exception.domain.AuctionException e) {
                        lockFailCount.incrementAndGet();   // AUCTION_LOCK_FAILED
                    } catch (Exception e) {
                        otherFailCount.incrementAndGet();  // 예상 외 예외
                    } finally {
                        doneLatch.countDown();
                    }
                });
            }

            startLatch.countDown();
            boolean completed = doneLatch.await(30, TimeUnit.SECONDS);
            executor.shutdown();

            assertThat(completed).as("모든 스레드가 제한 시간 내 완료되어야 함").isTrue();

            // 경매 최종 상태 조회 (JdbcTemplate으로 @SQLRestriction 우회)
            String auctionStatus = jdbcTemplate.queryForObject(
                    "SELECT status FROM auctions WHERE id = ?", String.class, auctionId);

            System.out.println("=== 즉시구매 동시성 결과 ===");
            System.out.printf("성공: %d건 | 락실패: %d건 | 기타실패: %d건 | 경매상태: %s%n",
                    successCount.get(), lockFailCount.get(), otherFailCount.get(), auctionStatus);

            assertThat(otherFailCount.get()).as("예상 외 예외 없어야 함").isZero();
            assertThat(successCount.get())
                    .as("즉시구매는 1건만 성공해야 함")
                    .isEqualTo(1);
            assertThat(lockFailCount.get())
                    .as("나머지는 AUCTION_LOCK_FAILED여야 함")
                    .isEqualTo(threadCount - 1);
            assertThat(auctionStatus)
                    .as("경매가 ENDED 상태로 마감되어야 함")
                    .isEqualTo("ENDED");

            // 주문 1건만 생성
            Long orderCount = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM orders WHERE auction_id = ?", Long.class, auctionId);
            assertThat(orderCount).as("즉시구매 주문은 1건만 생성되어야 함").isEqualTo(1L);
        }
    }
}
