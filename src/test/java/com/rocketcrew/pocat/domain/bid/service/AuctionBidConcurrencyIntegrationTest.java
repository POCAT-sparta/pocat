package com.rocketcrew.pocat.domain.bid.service;

import com.rocketcrew.pocat.cache.MockRedisTestConfig;
import com.rocketcrew.pocat.domain.auction.entity.Auction;
import com.rocketcrew.pocat.domain.auction.enums.AuctionStatus;
import com.rocketcrew.pocat.domain.auction.repository.AuctionRepository;
import com.rocketcrew.pocat.domain.auction.repository.AuctionSearchRepository;
import com.rocketcrew.pocat.domain.bid.dto.request.CreateBidRequest;
import com.rocketcrew.pocat.domain.bid.entity.AuctionBid;
import com.rocketcrew.pocat.domain.bid.enums.BidStatus;
import com.rocketcrew.pocat.domain.bid.repository.AuctionBidRepository;
import com.rocketcrew.pocat.domain.card.repository.CardSearchRepository;
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
import com.rocketcrew.pocat.domain.user.entity.User;
import com.rocketcrew.pocat.domain.user.enums.UserRole;
import com.rocketcrew.pocat.domain.user.repository.UserRepository;
import com.rocketcrew.pocat.global.outbox.service.OutboxEventWriter;
import com.rocketcrew.pocat.global.ratelimit.RedisRateLimiter;
import org.junit.jupiter.api.*;
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
 * 입찰 동시성 통합 테스트
 *
 * 실제 DB(H2)와 Spring 컨텍스트를 사용해 createBid() 동시 호출 동작을 검증한다.
 * 외부 의존성 (Redis, Redisson, PortOne, Kafka)은 @MockBean 으로 격리.
 *
 * ─────────────────────────────────────────────────────────────
 * 설계 의도
 * ─────────────────────────────────────────────────────────────
 * AuctionBidCommandService.createBid()는 Redisson 분산 락을 tryLock(0, SECONDS)로
 * 획득한다. 락 획득에 실패한 스레드는 즉시 BID_LOCK_FAILED로 반환된다.
 * 따라서 같은 경매에 대한 동시 입찰은 락을 획득한 스레드 1건만 성공하고
 * 나머지는 실패한다.
 *
 * 테스트에서는 Redisson을 AtomicBoolean으로 시뮬레이션해
 * 실제 Redis 없이 분산 락 직렬화 효과를 검증한다.
 * ─────────────────────────────────────────────────────────────
 */
@SpringBootTest
@Import(MockRedisTestConfig.class)
@DisplayName("입찰 동시성 통합 테스트")
class AuctionBidConcurrencyIntegrationTest {

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
    @MockBean private AuctionEventHandler auctionEventHandler;
    @MockBean private BidEventHandler bidEventHandler;
    @MockBean private OrderEventHandler orderEventHandler;
    @MockBean private PaymentEventHandler paymentEventHandler;
    @MockBean private RefundEventHandler refundEventHandler;
    @MockBean private SettlementEventHandler settlementEventHandler;
    @MockBean private NotificationEventHandler notificationEventHandler;

    // ── 실제 빈 ──────────────────────────────────────────────────
    @Autowired private AuctionBidCommandService auctionBidCommandService;
    @Autowired private AuctionBidRepository auctionBidRepository;
    @Autowired private AuctionRepository auctionRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private TransactionTemplate transactionTemplate;
    @Autowired private JdbcTemplate jdbcTemplate;

    private static final String SELLER_EMAIL = "it-bid-seller@test.com";
    private static final String BIDDER_EMAIL  = "it-bid-bidder@test.com";

    private Long sellerId;
    private Long bidderId;
    private Long auctionId;

    // ── 공통 세팅 ─────────────────────────────────────────────────

    @BeforeEach
    void setUp() throws Exception {
        // Redisson 분산 락을 AtomicBoolean으로 시뮬레이션
        // tryLock(0, SECONDS): 처음 호출한 스레드만 true, 이후 호출은 false → BID_LOCK_FAILED
        AtomicBoolean lockHeld = new AtomicBoolean(false);
        RLock mockLock = Mockito.mock(RLock.class);
        when(redissonClient.getLock(anyString())).thenReturn(mockLock);
        doAnswer(inv -> lockHeld.compareAndSet(false, true))
                .when(mockLock).tryLock(anyLong(), any(TimeUnit.class));
        when(mockLock.isHeldByCurrentThread()).thenReturn(true);
        doAnswer(inv -> { lockHeld.set(false); return null; }).when(mockLock).unlock();

        transactionTemplate.execute(status -> {
            User seller = userRepository.save(User.builder()
                    .email(SELLER_EMAIL)
                    .password("encoded-pw")
                    .nickname("판매자")
                    .userRole(UserRole.USER)
                    .build());
            sellerId = seller.getId();

            User bidder = userRepository.save(User.builder()
                    .email(BIDDER_EMAIL)
                    .password("encoded-pw")
                    .nickname("입찰자")
                    .userRole(UserRole.USER)
                    .billingKey("test-billing-key-001")
                    .build());
            bidderId = bidder.getId();

            Auction auction = auctionRepository.save(Auction.builder()
                    .cardId(1L)
                    .sellerId(sellerId)
                    .title("동시성 테스트 경매")
                    .description("테스트용 경매")
                    .startingPrice(1_000L)
                    .buyoutPrice(100_000L)
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
        // @SQLDelete(소프트 딜리트) 우회 — 실제 행 삭제
        jdbcTemplate.update("DELETE FROM auction_bids WHERE auction_id = ?", auctionId);
        jdbcTemplate.update("DELETE FROM auctions WHERE id = ?", auctionId);
        jdbcTemplate.update("DELETE FROM users WHERE email IN (?, ?)", SELLER_EMAIL, BIDDER_EMAIL);
    }

    // ── 테스트 ────────────────────────────────────────────────────

    @Nested
    @DisplayName("createBid() 동시성")
    class ConcurrentCreateBid {

        @Test
        @DisplayName("동시 입찰: Redisson 락으로 1건만 성공, 나머지는 즉시 실패")
        void concurrent_createBid_onlyOneSucceeds() throws InterruptedException {
            int threadCount = 10;
            ExecutorService executor = Executors.newFixedThreadPool(threadCount);
            CountDownLatch startLatch = new CountDownLatch(1);
            CountDownLatch doneLatch  = new CountDownLatch(threadCount);

            AtomicInteger successCount = new AtomicInteger(0);
            AtomicInteger failCount    = new AtomicInteger(0);

            CreateBidRequest request = new CreateBidRequest(2_000L);

            for (int i = 0; i < threadCount; i++) {
                executor.submit(() -> {
                    try {
                        startLatch.await();
                        auctionBidCommandService.createBid(bidderId, auctionId, request);
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

            List<AuctionBid> bids = auctionBidRepository.findAllByAuctionId(auctionId);

            System.out.println("=== 입찰 동시성 결과 ===");
            System.out.printf("성공: %d건 | 실패: %d건 | 생성된 입찰: %d건%n",
                    successCount.get(), failCount.get(), bids.size());

            // Redisson 락(tryLock 0초)으로 1건만 성공
            assertThat(successCount.get())
                    .as("Redisson 락 직렬화로 동시 입찰 중 1건만 성공해야 함")
                    .isEqualTo(1);
            assertThat(failCount.get())
                    .as("락 획득 실패로 나머지 스레드는 모두 실패해야 함")
                    .isEqualTo(threadCount - 1);

            // DB에 LEADING 입찰 1건만 존재
            assertThat(bids).hasSize(1);
            assertThat(bids.get(0).getStatus()).isEqualTo(BidStatus.LEADING);
            assertThat(bids.get(0).getBidPrice()).isEqualTo(2_000L);
        }
    }
}
