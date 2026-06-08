package com.rocketcrew.pocat.domain.bid.service;

import com.rocketcrew.pocat.domain.auction.entity.Auction;
import com.rocketcrew.pocat.domain.auction.enums.AuctionStatus;
import com.rocketcrew.pocat.domain.auction.service.AuctionEsIndexService;
import com.rocketcrew.pocat.domain.auction.service.AuctionQueryService;
import com.rocketcrew.pocat.global.outbox.service.OutboxEventWriter;
import com.rocketcrew.pocat.domain.bid.dto.request.CreateBidRequest;
import com.rocketcrew.pocat.domain.bid.dto.response.CreateAuctionBidResponse;
import com.rocketcrew.pocat.domain.bid.entity.AuctionBid;
import com.rocketcrew.pocat.domain.bid.enums.BidStatus;
import com.rocketcrew.pocat.domain.bid.event.BidOutbidEvent;
import com.rocketcrew.pocat.domain.bid.repository.AuctionBidRepository;
import com.rocketcrew.pocat.domain.user.entity.User;
import com.rocketcrew.pocat.domain.user.enums.UserRole;
import com.rocketcrew.pocat.domain.user.service.UserQueryService;
import com.rocketcrew.pocat.global.exception.common.ErrorCode;
import com.rocketcrew.pocat.global.exception.domain.AuctionException;
import com.rocketcrew.pocat.global.exception.domain.BidException;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AuctionBidCommandServiceTest {

    @InjectMocks
    AuctionBidCommandService service;

    @Mock
    AuctionBidRepository auctionBidRepository;

    @Mock
    AuctionQueryService auctionQueryService;

    @Mock
    UserQueryService userQueryService;

    @Mock
    EntityManager entityManager;

    @Mock
    RedissonClient redissonClient;

    @Mock
    RLock rLock;

    @Mock
    ApplicationEventPublisher eventPublisher;

    @Mock
    AuctionEsIndexService auctionEsIndexService;

    @Mock
    OutboxEventWriter outboxEventWriter;

    @Mock
    com.rocketcrew.pocat.global.metrics.BidMetrics bidMetrics;

    // ---------------------------------------------------------------
    // Test fixtures
    // ---------------------------------------------------------------
    private Auction activeAuction;
    private User normalBidder;

    @BeforeEach
    void setUp() throws InterruptedException {
        // Lock setup
        given(redissonClient.getLock(anyString())).willReturn(rLock);
        given(rLock.tryLock(anyLong(), any(TimeUnit.class))).willReturn(true);
        doNothing().when(entityManager).detach(any());

        // Active auction: sellerId=2, not expired
        activeAuction = Auction.builder()
                .cardId(1L)
                .sellerId(2L)
                .title("경매")
                .startingPrice(1000L)
                .buyoutPrice(10000L)
                .status(AuctionStatus.ACTIVE)
                .startedAt(LocalDateTime.now(ZoneId.of("Asia/Seoul")).minusHours(1))
                .endedAt(LocalDateTime.now(ZoneId.of("Asia/Seoul")).plusHours(1))
                .build();
        ReflectionTestUtils.setField(activeAuction, "id", 1L);

        // Normal bidder: userId=3, not blocked, has billing key
        normalBidder = User.builder()
                .email("bidder@test.com")
                .password("pw")
                .nickname("입찰자")
                .userRole(UserRole.USER)
                .billingKey("bkey-001")
                .build();
        ReflectionTestUtils.setField(normalBidder, "id", 3L);

        // Activate TransactionSynchronizationManager for this thread
        TransactionSynchronizationManager.initSynchronization();
    }

    @AfterEach
    void tearDown() {
        TransactionSynchronizationManager.clearSynchronization();
    }

    // ---------------------------------------------------------------
    // Success
    // ---------------------------------------------------------------
    @Nested
    @DisplayName("createBid 성공")
    class CreateBidSuccess {

        @Test
        @DisplayName("성공: ACTIVE 경매에 유효한 입찰가로 입찰 생성")
        void success() throws Exception {
            // given
            CreateBidRequest request = new CreateBidRequest(2000L);
            given(userQueryService.getUserEntity(3L)).willReturn(normalBidder);
            given(auctionQueryService.findAuctionEntityOrThrow(1L))
                    .willReturn(activeAuction)   // pre-lock call
                    .willReturn(activeAuction);  // post-lock (after detach)

            given(rLock.isHeldByCurrentThread()).willReturn(true);

            AuctionBid savedBid = AuctionBid.builder()
                    .userId(3L)
                    .auctionId(1L)
                    .bidPrice(2000L)
                    .status(BidStatus.LEADING)
                    .build();
            ReflectionTestUtils.setField(savedBid, "id", 10L);
            given(auctionBidRepository.save(any(AuctionBid.class))).willReturn(savedBid);
            given(auctionBidRepository
                    .findFirstByAuctionIdAndUserIdAndStatusOrderByBidPriceDescCreatedAtDesc(any(), any(), any()))
                    .willReturn(Optional.empty());

            // when
            CreateAuctionBidResponse response = service.createBid(3L, 1L, request);

            // then
            assertThat(response.bidPrice()).isEqualTo(2000L);
            assertThat(response.status()).isEqualTo(BidStatus.LEADING);
            // 락 획득 여부 검증 (unlock은 afterCompletion 콜백에서 실행되므로 트랜잭션 없는 단위 테스트에서는 검증 생략)
            verify(rLock).tryLock(anyLong(), any(TimeUnit.class));
            verify(eventPublisher, never()).publishEvent(any(BidOutbidEvent.class));
        }

        @Test
        @DisplayName("성공: 기존 선두 입찰자가 있으면 OUTBID 이벤트를 발행한다")
        void successPublishOutbidEvent() throws Exception {
            // given
            Auction auctionWithHighestBidder = Auction.builder()
                    .cardId(1L)
                    .sellerId(2L)
                    .title("경매")
                    .startingPrice(1000L)
                    .buyoutPrice(10000L)
                    .status(AuctionStatus.ACTIVE)
                    .startedAt(LocalDateTime.now(ZoneId.of("Asia/Seoul")).minusHours(1))
                    .endedAt(LocalDateTime.now(ZoneId.of("Asia/Seoul")).plusHours(1))
                    .build();
            ReflectionTestUtils.setField(auctionWithHighestBidder, "id", 1L);
            ReflectionTestUtils.setField(auctionWithHighestBidder, "highestBidderId", 4L);
            ReflectionTestUtils.setField(auctionWithHighestBidder, "highestPrice", 1500L);

            AuctionBid previousLeadingBid = AuctionBid.builder()
                    .userId(4L)
                    .auctionId(1L)
                    .bidPrice(1500L)
                    .status(BidStatus.LEADING)
                    .build();
            ReflectionTestUtils.setField(previousLeadingBid, "id", 9L);

            AuctionBid savedBid = AuctionBid.builder()
                    .userId(3L)
                    .auctionId(1L)
                    .bidPrice(2000L)
                    .status(BidStatus.LEADING)
                    .build();
            ReflectionTestUtils.setField(savedBid, "id", 10L);

            CreateBidRequest request = new CreateBidRequest(2000L);
            given(userQueryService.getUserEntity(3L)).willReturn(normalBidder);
            given(auctionQueryService.findAuctionEntityOrThrow(1L))
                    .willReturn(auctionWithHighestBidder)
                    .willReturn(auctionWithHighestBidder);
            given(auctionBidRepository
                    .findFirstByAuctionIdAndUserIdAndStatusOrderByBidPriceDescCreatedAtDesc(1L, 4L, BidStatus.LEADING))
                    .willReturn(Optional.of(previousLeadingBid));
            given(auctionBidRepository.save(any(AuctionBid.class))).willReturn(savedBid);

            // when
            CreateAuctionBidResponse response = service.createBid(3L, 1L, request);

            // then
            assertThat(response.bidPrice()).isEqualTo(2000L);
            assertThat(previousLeadingBid.getStatus()).isEqualTo(BidStatus.OUTBID);
            verify(eventPublisher).publishEvent(ArgumentMatchers.<Object>argThat(event -> {
                if (!(event instanceof BidOutbidEvent outbidEvent)) {
                    return false;
                }
                return outbidEvent.getAuctionId().equals(1L)
                        && outbidEvent.getPreviousBidderId().equals(4L)
                        && outbidEvent.getCurrentHighestPrice().equals(2000L);
            }));
        }
    }

    // ---------------------------------------------------------------
    // Failure cases
    // ---------------------------------------------------------------
    @Nested
    @DisplayName("createBid 실패")
    class CreateBidFailures {

        @Test
        @DisplayName("실패: request가 null → INVALID_INPUT")
        void failNullRequest() {
            assertThatThrownBy(() -> service.createBid(3L, 1L, null))
                    .isInstanceOf(BidException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_INPUT);
        }

        @Test
        @DisplayName("실패: ACTIVE가 아닌 경매 → AUCTION_NOT_ACTIVE")
        void failAuctionNotActive() {
            // given
            Auction pendingAuction = Auction.builder()
                    .cardId(1L).sellerId(2L).title("경매")
                    .startingPrice(1000L).buyoutPrice(10000L)
                    .status(AuctionStatus.PENDING)
                    .startedAt(LocalDateTime.now(ZoneId.of("Asia/Seoul")).minusHours(1))
                    .endedAt(LocalDateTime.now(ZoneId.of("Asia/Seoul")).plusHours(1))
                    .build();
            ReflectionTestUtils.setField(pendingAuction, "id", 1L);
            given(userQueryService.getUserEntity(3L)).willReturn(normalBidder);
            given(auctionQueryService.findAuctionEntityOrThrow(1L)).willReturn(pendingAuction);
            CreateBidRequest request = new CreateBidRequest(2000L);

            // when / then
            assertThatThrownBy(() -> service.createBid(3L, 1L, request))
                    .isInstanceOf(AuctionException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.AUCTION_NOT_ACTIVE);
        }

        @Test
        @DisplayName("실패: 경매 종료 시각 초과 → AUCTION_NOT_ACTIVE")
        void failAuctionExpired() {
            // given
            Auction expiredAuction = Auction.builder()
                    .cardId(1L).sellerId(2L).title("경매")
                    .startingPrice(1000L).buyoutPrice(10000L)
                    .status(AuctionStatus.ACTIVE)
                    .startedAt(LocalDateTime.now(ZoneId.of("Asia/Seoul")).minusHours(2))
                    .endedAt(LocalDateTime.now(ZoneId.of("Asia/Seoul")).minusMinutes(1))   // already ended
                    .build();
            ReflectionTestUtils.setField(expiredAuction, "id", 1L);
            given(userQueryService.getUserEntity(3L)).willReturn(normalBidder);
            given(auctionQueryService.findAuctionEntityOrThrow(1L)).willReturn(expiredAuction);
            CreateBidRequest request = new CreateBidRequest(2000L);

            // when / then
            assertThatThrownBy(() -> service.createBid(3L, 1L, request))
                    .isInstanceOf(AuctionException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.AUCTION_NOT_ACTIVE);
        }

        @Test
        @DisplayName("실패: 입찰 차단 사용자 → BID_BLOCKED_USER")
        void failBlockedUser() {
            // given
            User blockedUser = User.builder()
                    .email("blocked@test.com").password("pw").nickname("차단유저")
                    .userRole(UserRole.USER).billingKey("bkey")
                    .isBidBlocked(true)
                    .build();
            ReflectionTestUtils.setField(blockedUser, "id", 3L);
            given(userQueryService.getUserEntity(3L)).willReturn(blockedUser);
            given(auctionQueryService.findAuctionEntityOrThrow(1L)).willReturn(activeAuction);
            CreateBidRequest request = new CreateBidRequest(2000L);

            // when / then
            assertThatThrownBy(() -> service.createBid(3L, 1L, request))
                    .isInstanceOf(BidException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.BID_BLOCKED_USER);
        }

        @Test
        @DisplayName("실패: 판매자 본인 경매 입찰 → BID_SELLER_FORBIDDEN")
        void failSellerBidsOwn() {
            // given
            User seller = User.builder()
                    .email("seller@test.com").password("pw").nickname("판매자")
                    .userRole(UserRole.USER).billingKey("bkey")
                    .build();
            ReflectionTestUtils.setField(seller, "id", 2L);   // sellerId == 2L in activeAuction
            given(userQueryService.getUserEntity(2L)).willReturn(seller);
            given(auctionQueryService.findAuctionEntityOrThrow(1L)).willReturn(activeAuction);
            CreateBidRequest request = new CreateBidRequest(2000L);

            // when / then
            assertThatThrownBy(() -> service.createBid(2L, 1L, request))
                    .isInstanceOf(BidException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.BID_SELLER_FORBIDDEN);
        }

        @Test
        @DisplayName("실패: 빌링키 없음 → BID_BILLING_KEY_REQUIRED")
        void failNoBillingKey() {
            // given
            User noBillingUser = User.builder()
                    .email("nb@test.com").password("pw").nickname("미등록")
                    .userRole(UserRole.USER)
                    .build();   // billingKey is null by default
            ReflectionTestUtils.setField(noBillingUser, "id", 3L);
            given(userQueryService.getUserEntity(3L)).willReturn(noBillingUser);
            given(auctionQueryService.findAuctionEntityOrThrow(1L)).willReturn(activeAuction);
            CreateBidRequest request = new CreateBidRequest(2000L);

            // when / then
            assertThatThrownBy(() -> service.createBid(3L, 1L, request))
                    .isInstanceOf(BidException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.BID_BILLING_KEY_REQUIRED);
        }

        @Test
        @DisplayName("실패: 이미 최고 입찰자 → BID_ALREADY_LEADING")
        void failAlreadyLeading() throws Exception {
            // given
            // Set highest bidder to be userId=3 (same as bidder)
            Auction auctionWithHighestBidder = Auction.builder()
                    .cardId(1L).sellerId(2L).title("경매")
                    .startingPrice(1000L).buyoutPrice(10000L)
                    .status(AuctionStatus.ACTIVE)
                    .startedAt(LocalDateTime.now(ZoneId.of("Asia/Seoul")).minusHours(1))
                    .endedAt(LocalDateTime.now(ZoneId.of("Asia/Seoul")).plusHours(1))
                    .build();
            ReflectionTestUtils.setField(auctionWithHighestBidder, "id", 1L);
            // Set highestBidderId to 3L via reflection
            ReflectionTestUtils.setField(auctionWithHighestBidder, "highestBidderId", 3L);
            ReflectionTestUtils.setField(auctionWithHighestBidder, "highestPrice", 1500L);

            given(userQueryService.getUserEntity(3L)).willReturn(normalBidder);
            given(auctionQueryService.findAuctionEntityOrThrow(1L))
                    .willReturn(auctionWithHighestBidder)
                    .willReturn(auctionWithHighestBidder);
            given(rLock.tryLock(anyLong(), any(TimeUnit.class))).willReturn(true);
            CreateBidRequest request = new CreateBidRequest(2000L);

            // when / then
            assertThatThrownBy(() -> service.createBid(3L, 1L, request))
                    .isInstanceOf(BidException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.BID_ALREADY_LEADING);
        }

        @Test
        @DisplayName("실패: 즉시구매가 이상 입찰 → BID_BUYOUT_PRICE_NOT_ALLOWED")
        void failBuyoutPriceNotAllowed() throws Exception {
            // given
            given(userQueryService.getUserEntity(3L)).willReturn(normalBidder);
            given(auctionQueryService.findAuctionEntityOrThrow(1L))
                    .willReturn(activeAuction)
                    .willReturn(activeAuction);
            given(rLock.tryLock(anyLong(), any(TimeUnit.class))).willReturn(true);
            // buyoutPrice is 10000, bid at 10000 or above
            CreateBidRequest request = new CreateBidRequest(10000L);

            // when / then
            assertThatThrownBy(() -> service.createBid(3L, 1L, request))
                    .isInstanceOf(BidException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.BID_BUYOUT_PRICE_NOT_ALLOWED);
        }

        @Test
        @DisplayName("실패: 입찰가 시작가보다 낮음 → BID_PRICE_TOO_LOW")
        void failBidPriceTooLow() throws Exception {
            // given
            given(userQueryService.getUserEntity(3L)).willReturn(normalBidder);
            given(auctionQueryService.findAuctionEntityOrThrow(1L))
                    .willReturn(activeAuction)
                    .willReturn(activeAuction);
            given(rLock.tryLock(anyLong(), any(TimeUnit.class))).willReturn(true);
            // startingPrice is 1000, bid at 500
            CreateBidRequest request = new CreateBidRequest(500L);

            // when / then
            assertThatThrownBy(() -> service.createBid(3L, 1L, request))
                    .isInstanceOf(BidException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.BID_PRICE_TOO_LOW);
        }

        @Test
        @DisplayName("실패: 현재 최고가보다 낮은 입찰가 → BID_PRICE_TOO_LOW")
        void failBidPriceLowerThanHighest() throws Exception {
            // given
            Auction auctionWithHighest = Auction.builder()
                    .cardId(1L).sellerId(2L).title("경매")
                    .startingPrice(1000L).buyoutPrice(10000L)
                    .status(AuctionStatus.ACTIVE)
                    .startedAt(LocalDateTime.now(ZoneId.of("Asia/Seoul")).minusHours(1))
                    .endedAt(LocalDateTime.now(ZoneId.of("Asia/Seoul")).plusHours(1))
                    .build();
            ReflectionTestUtils.setField(auctionWithHighest, "id", 1L);
            ReflectionTestUtils.setField(auctionWithHighest, "highestBidderId", 99L);  // someone else
            ReflectionTestUtils.setField(auctionWithHighest, "highestPrice", 3000L);

            given(userQueryService.getUserEntity(3L)).willReturn(normalBidder);
            given(auctionQueryService.findAuctionEntityOrThrow(1L))
                    .willReturn(auctionWithHighest)
                    .willReturn(auctionWithHighest);
            given(rLock.tryLock(anyLong(), any(TimeUnit.class))).willReturn(true);
            // bid 2000 <= highestPrice 3000
            CreateBidRequest request = new CreateBidRequest(2000L);

            // when / then
            assertThatThrownBy(() -> service.createBid(3L, 1L, request))
                    .isInstanceOf(BidException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.BID_PRICE_TOO_LOW);
        }

        @Test
        @DisplayName("실패: 락 획득 실패 → BID_LOCK_FAILED")
        void failLockFailed() throws InterruptedException {
            // given
            given(userQueryService.getUserEntity(3L)).willReturn(normalBidder);
            given(auctionQueryService.findAuctionEntityOrThrow(1L)).willReturn(activeAuction);
            given(rLock.tryLock(anyLong(), any(TimeUnit.class))).willReturn(false);
            CreateBidRequest request = new CreateBidRequest(2000L);

            // when / then
            assertThatThrownBy(() -> service.createBid(3L, 1L, request))
                    .isInstanceOf(BidException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.BID_LOCK_FAILED);
        }
    }
}
