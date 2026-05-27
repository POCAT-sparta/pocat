package com.rocketcrew.pocat.domain.auction.service;

import com.rocketcrew.pocat.domain.auction.dto.request.AdminCancelAuctionRequest;
import com.rocketcrew.pocat.domain.auction.dto.request.CreateAuctionRequest;
import com.rocketcrew.pocat.domain.auction.dto.request.InspectAuctionRequest;
import com.rocketcrew.pocat.domain.auction.dto.request.UpdateAuctionRequest;
import com.rocketcrew.pocat.domain.auction.dto.response.AdminCancelAuctionResponse;
import com.rocketcrew.pocat.domain.auction.dto.response.CancelAuctionResponse;
import com.rocketcrew.pocat.domain.auction.dto.response.CreateAuctionResponse;
import com.rocketcrew.pocat.domain.auction.dto.response.InspectAuctionResponse;
import com.rocketcrew.pocat.domain.auction.dto.response.UpdateAuctionResponse;
import com.rocketcrew.pocat.domain.auction.entity.Auction;
import com.rocketcrew.pocat.domain.auction.enums.AuctionInspectionResult;
import com.rocketcrew.pocat.domain.auction.enums.AuctionStatus;
import com.rocketcrew.pocat.domain.auction.event.AuctionCancelledEvent;
import com.rocketcrew.pocat.domain.auction.event.AuctionInspectionFailedEvent;
import com.rocketcrew.pocat.domain.auction.event.AuctionInspectionPassedEvent;
import com.rocketcrew.pocat.domain.auction.repository.AuctionRepository;
import com.rocketcrew.pocat.domain.bid.repository.AuctionBidRepository;
import com.rocketcrew.pocat.domain.card.entity.Card;
import com.rocketcrew.pocat.domain.card.entity.enums.CardCategory;
import com.rocketcrew.pocat.domain.card.entity.enums.CardGrade;
import com.rocketcrew.pocat.domain.card.entity.enums.CardSource;
import com.rocketcrew.pocat.domain.card.entity.enums.CardStatus;
import com.rocketcrew.pocat.domain.card.service.CardQueryService;
import com.rocketcrew.pocat.domain.user.entity.User;
import com.rocketcrew.pocat.domain.user.enums.UserRole;
import com.rocketcrew.pocat.domain.user.service.UserQueryService;
import com.rocketcrew.pocat.global.exception.common.ErrorCode;
import com.rocketcrew.pocat.global.exception.domain.AuctionException;
import com.rocketcrew.pocat.global.exception.domain.CardException;
import com.rocketcrew.pocat.global.exception.domain.UserException;
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

import java.util.Collections;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AuctionCommandServiceTest {

    @InjectMocks
    AuctionCommandService service;

    @Mock
    AuctionRepository auctionRepository;

    @Mock
    AuctionBidRepository auctionBidRepository;

    @Mock
    CardQueryService cardQueryService;

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

    private Card activeCard;

    @BeforeEach
    void setUp() throws InterruptedException {
        given(redissonClient.getLock(anyString())).willReturn(rLock);
        given(rLock.tryLock(anyLong(), any(TimeUnit.class))).willReturn(true);
        doNothing().when(entityManager).detach(any());

        activeCard = Card.builder()
                .userId(1L)
                .name("피카츄")
                .series("SV")
                .setId("sv1")
                .setName("스칼렛 & 바이올렛")
                .cardNumber("001")
                .rarity("RR")
                .category(CardCategory.POKEMON)
                .grade(CardGrade.PSA_10)
                .source(CardSource.TCGDEX)
                .status(CardStatus.ACTIVE)
                .build();
        ReflectionTestUtils.setField(activeCard, "id", 1L);

        TransactionSynchronizationManager.initSynchronization();
    }

    @AfterEach
    void tearDown() {
        TransactionSynchronizationManager.clearSynchronization();
    }

    // ---------------------------------------------------------------
    // Helper
    // ---------------------------------------------------------------
    private Auction buildAuction(Long id, Long sellerId, AuctionStatus status) {
        Auction auction = Auction.builder()
                .cardId(1L)
                .sellerId(sellerId)
                .title("테스트경매")
                .description("설명")
                .startingPrice(1000L)
                .buyoutPrice(10000L)
                .status(status)
                .build();
        ReflectionTestUtils.setField(auction, "id", id);
        return auction;
    }

    // ---------------------------------------------------------------
    // createAuction
    // ---------------------------------------------------------------
    @Nested
    @DisplayName("createAuction")
    class CreateAuction {

        @Test
        @DisplayName("성공: 유효한 요청으로 경매 생성")
        void success() {
            // given
            CreateAuctionRequest request = new CreateAuctionRequest(1L, "테스트경매", "설명", 1000L, 10000L);
            Auction saved = buildAuction(1L, 2L, AuctionStatus.PENDING);
            given(cardQueryService.validateRegistrableForAuction(1L)).willReturn(activeCard);
            given(auctionRepository.save(any(Auction.class))).willReturn(saved);

            // when
            CreateAuctionResponse response = service.createAuction(2L, request);

            // then
            assertThat(response.auctionId()).isEqualTo(1L);
            assertThat(response.status()).isEqualTo(AuctionStatus.PENDING);
        }

        @Test
        @DisplayName("실패: 즉시구매가 <= 시작가 → AUCTION_PRICE_INVALID")
        void failBuyoutPriceTooLow() {
            // given
            CreateAuctionRequest request = new CreateAuctionRequest(1L, "테스트경매", "설명", 5000L, 3000L);
            given(cardQueryService.validateRegistrableForAuction(1L)).willReturn(activeCard);

            // when / then
            assertThatThrownBy(() -> service.createAuction(2L, request))
                    .isInstanceOf(AuctionException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.AUCTION_PRICE_INVALID);
        }

        @Test
        @DisplayName("실패: 카드 검증 실패 → CardException 전파")
        void failCardValidation() {
            // given
            CreateAuctionRequest request = new CreateAuctionRequest(99L, "테스트경매", "설명", 1000L, 10000L);
            given(cardQueryService.validateRegistrableForAuction(99L))
                    .willThrow(new CardException(ErrorCode.CARD_NOT_ACTIVE));

            // when / then
            assertThatThrownBy(() -> service.createAuction(2L, request))
                    .isInstanceOf(CardException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.CARD_NOT_ACTIVE);
        }
    }

    // ---------------------------------------------------------------
    // updateAuction
    // ---------------------------------------------------------------
    @Nested
    @DisplayName("updateAuction")
    class UpdateAuction {

        @Test
        @DisplayName("성공: PENDING 상태 경매 수정")
        void success() {
            // given
            Auction auction = buildAuction(1L, 2L, AuctionStatus.PENDING);
            given(auctionRepository.findById(1L)).willReturn(Optional.of(auction));
            UpdateAuctionRequest request = new UpdateAuctionRequest("새제목", null, null, null);

            // when
            UpdateAuctionResponse response = service.updateAuction(2L, 1L, request);

            // then
            assertThat(response.title()).isEqualTo("새제목");
        }

        @Test
        @DisplayName("실패: 존재하지 않는 경매 → AUCTION_NOT_FOUND")
        void failNotFound() {
            // given
            given(auctionRepository.findById(999L)).willReturn(Optional.empty());
            UpdateAuctionRequest request = new UpdateAuctionRequest("새제목", null, null, null);

            // when / then
            assertThatThrownBy(() -> service.updateAuction(2L, 999L, request))
                    .isInstanceOf(AuctionException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.AUCTION_NOT_FOUND);
        }

        @Test
        @DisplayName("실패: 판매자 불일치 → USER_FORBIDDEN")
        void failForbidden() {
            // given
            Auction auction = buildAuction(1L, 2L, AuctionStatus.PENDING);
            given(auctionRepository.findById(1L)).willReturn(Optional.of(auction));
            UpdateAuctionRequest request = new UpdateAuctionRequest("새제목", null, null, null);

            // when / then
            assertThatThrownBy(() -> service.updateAuction(99L, 1L, request))
                    .isInstanceOf(AuctionException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.USER_FORBIDDEN);
        }

        @Test
        @DisplayName("실패: PENDING이 아닌 경매 수정 → AUCTION_NOT_PENDING")
        void failNotPending() {
            // given
            Auction auction = buildAuction(1L, 2L, AuctionStatus.ACTIVE);
            given(auctionRepository.findById(1L)).willReturn(Optional.of(auction));
            UpdateAuctionRequest request = new UpdateAuctionRequest("새제목", null, null, null);

            // when / then
            assertThatThrownBy(() -> service.updateAuction(2L, 1L, request))
                    .isInstanceOf(AuctionException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.AUCTION_NOT_PENDING);
        }

        @Test
        @DisplayName("실패: 모든 필드 null → AUCTION_UPDATE_EMPTY")
        void failEmptyRequest() {
            // given
            Auction auction = buildAuction(1L, 2L, AuctionStatus.PENDING);
            given(auctionRepository.findById(1L)).willReturn(Optional.of(auction));
            UpdateAuctionRequest request = new UpdateAuctionRequest(null, null, null, null);

            // when / then
            assertThatThrownBy(() -> service.updateAuction(2L, 1L, request))
                    .isInstanceOf(AuctionException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.AUCTION_UPDATE_EMPTY);
        }

        @Test
        @DisplayName("실패: 즉시구매가 <= 시작가 → AUCTION_PRICE_INVALID")
        void failBuyoutPriceInvalid() {
            // given
            Auction auction = buildAuction(1L, 2L, AuctionStatus.PENDING);
            given(auctionRepository.findById(1L)).willReturn(Optional.of(auction));
            // existing startingPrice = 1000, update buyoutPrice to 500 which is <= startingPrice
            UpdateAuctionRequest request = new UpdateAuctionRequest(null, null, null, 500L);

            // when / then
            assertThatThrownBy(() -> service.updateAuction(2L, 1L, request))
                    .isInstanceOf(AuctionException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.AUCTION_PRICE_INVALID);
        }
    }

    // ---------------------------------------------------------------
    // cancelAuction (seller)
    // ---------------------------------------------------------------
    @Nested
    @DisplayName("cancelAuction (판매자)")
    class SellerCancelAuction {

        @Test
        @DisplayName("성공: PENDING 경매 판매자 취소")
        void success() {
            // given
            Auction auction = buildAuction(1L, 2L, AuctionStatus.PENDING);
            given(auctionRepository.findById(1L)).willReturn(Optional.of(auction));

            // when
            CancelAuctionResponse response = service.cancelAuction(2L, 1L);

            // then
            assertThat(response.auctionId()).isEqualTo(1L);
            assertThat(response.status()).isEqualTo(AuctionStatus.CANCELLED);
            verify(eventPublisher, never()).publishEvent(any());
        }

        @Test
        @DisplayName("실패: 경매 미존재 → AUCTION_NOT_FOUND")
        void failNotFound() {
            // given
            given(auctionRepository.findById(999L)).willReturn(Optional.empty());

            // when / then
            assertThatThrownBy(() -> service.cancelAuction(2L, 999L))
                    .isInstanceOf(AuctionException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.AUCTION_NOT_FOUND);
        }

        @Test
        @DisplayName("실패: 판매자 불일치 → USER_FORBIDDEN")
        void failForbidden() {
            // given
            Auction auction = buildAuction(1L, 2L, AuctionStatus.PENDING);
            given(auctionRepository.findById(1L)).willReturn(Optional.of(auction));

            // when / then
            assertThatThrownBy(() -> service.cancelAuction(99L, 1L))
                    .isInstanceOf(AuctionException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.USER_FORBIDDEN);
        }

        @Test
        @DisplayName("실패: ACTIVE 경매 취소 → AUCTION_NOT_PENDING")
        void failNotPending() {
            // given
            Auction auction = buildAuction(1L, 2L, AuctionStatus.ACTIVE);
            given(auctionRepository.findById(1L)).willReturn(Optional.of(auction));

            // when / then
            assertThatThrownBy(() -> service.cancelAuction(2L, 1L))
                    .isInstanceOf(AuctionException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.AUCTION_NOT_PENDING);
        }
    }

    // ---------------------------------------------------------------
    // cancelAuction (admin)
    // ---------------------------------------------------------------
    @Nested
    @DisplayName("cancelAuction (관리자)")
    class AdminCancelAuction {

        @Test
        @DisplayName("성공: PENDING 경매 관리자 취소")
        void success() {
            // given
            Auction auction = buildAuction(1L, 2L, AuctionStatus.PENDING);
            given(auctionRepository.findById(1L))
                    .willReturn(Optional.of(auction))  // first call
                    .willReturn(Optional.of(auction)); // second call after detach
            given(auctionBidRepository.findDistinctBidderIdsByAuctionId(1L))
                    .willReturn(Collections.emptyList());
            given(auctionBidRepository.findFirstByAuctionIdAndStatusOrderByBidPriceDescCreatedAtDesc(any(), any()))
                    .willReturn(Optional.empty());
            AdminCancelAuctionRequest request = new AdminCancelAuctionRequest("정책 위반");

            // when
            AdminCancelAuctionResponse response = service.cancelAuction(1L, 1L, request);

            // then
            assertThat(response.status()).isEqualTo(AuctionStatus.CANCELLED);
            assertThat(response.reason()).isEqualTo("정책 위반");
            verify(eventPublisher).publishEvent(ArgumentMatchers.<Object>argThat(event -> {
                if (!(event instanceof AuctionCancelledEvent cancelledEvent)) {
                    return false;
                }
                return cancelledEvent.getAuctionId().equals(1L)
                        && cancelledEvent.getSellerId().equals(2L)
                        && cancelledEvent.getCancelledBy().equals(1L)
                        && cancelledEvent.getReason().equals("정책 위반");
            }));
        }

        @Test
        @DisplayName("실패: 경매 미존재 → AUCTION_NOT_FOUND")
        void failNotFound() {
            // given
            given(auctionRepository.findById(999L)).willReturn(Optional.empty());
            AdminCancelAuctionRequest request = new AdminCancelAuctionRequest("정책 위반");

            // when / then
            assertThatThrownBy(() -> service.cancelAuction(1L, 999L, request))
                    .isInstanceOf(AuctionException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.AUCTION_NOT_FOUND);
        }

        @Test
        @DisplayName("실패: 사유 미입력 → AUCTION_REASON_REQUIRED")
        void failNoReason() {
            // given
            Auction auction = buildAuction(1L, 2L, AuctionStatus.PENDING);
            given(auctionRepository.findById(1L)).willReturn(Optional.of(auction));
            AdminCancelAuctionRequest request = new AdminCancelAuctionRequest("   ");

            // when / then
            assertThatThrownBy(() -> service.cancelAuction(1L, 1L, request))
                    .isInstanceOf(AuctionException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.AUCTION_REASON_REQUIRED);
        }

        @Test
        @DisplayName("실패: 이미 CANCELLED 상태 → AUCTION_CANNOT_CANCEL")
        void failAlreadyCancelled() {
            // given
            Auction auction = buildAuction(1L, 2L, AuctionStatus.CANCELLED);
            given(auctionRepository.findById(1L)).willReturn(Optional.of(auction));
            AdminCancelAuctionRequest request = new AdminCancelAuctionRequest("정책 위반");

            // when / then
            assertThatThrownBy(() -> service.cancelAuction(1L, 1L, request))
                    .isInstanceOf(AuctionException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.AUCTION_CANNOT_CANCEL);
        }

        @Test
        @DisplayName("실패: ENDED 상태 → AUCTION_CANNOT_CANCEL")
        void failEnded() {
            // given
            Auction auction = buildAuction(1L, 2L, AuctionStatus.ENDED);
            given(auctionRepository.findById(1L)).willReturn(Optional.of(auction));
            AdminCancelAuctionRequest request = new AdminCancelAuctionRequest("정책 위반");

            // when / then
            assertThatThrownBy(() -> service.cancelAuction(1L, 1L, request))
                    .isInstanceOf(AuctionException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.AUCTION_CANNOT_CANCEL);
        }

        @Test
        @DisplayName("실패: 락 획득 실패 → AUCTION_LOCK_FAILED")
        void failLockFailed() throws InterruptedException {
            // given
            Auction auction = buildAuction(1L, 2L, AuctionStatus.PENDING);
            given(auctionRepository.findById(1L)).willReturn(Optional.of(auction));
            given(rLock.tryLock(anyLong(), any(TimeUnit.class))).willReturn(false);
            AdminCancelAuctionRequest request = new AdminCancelAuctionRequest("정책 위반");

            // when / then
            assertThatThrownBy(() -> service.cancelAuction(1L, 1L, request))
                    .isInstanceOf(AuctionException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.AUCTION_LOCK_FAILED);
        }
    }

    // ---------------------------------------------------------------
    // inspectAuction
    // ---------------------------------------------------------------
    @Nested
    @DisplayName("inspectAuction")
    class InspectAuction {

        @Test
        @DisplayName("성공: PASSED - PENDING 경매 승인")
        void successPassed() {
            // given
            Auction auction = buildAuction(1L, 2L, AuctionStatus.PENDING);
            given(auctionRepository.findById(1L))
                    .willReturn(Optional.of(auction))
                    .willReturn(Optional.of(auction));
            User seller = User.builder().email("s@test.com").password("pw").nickname("판매자")
                    .userRole(UserRole.USER).billingKey("bkey").build();
            ReflectionTestUtils.setField(seller, "id", 2L);
            given(userQueryService.getUserEntity(2L)).willReturn(seller);
            given(cardQueryService.validateRegistrableForAuction(1L)).willReturn(activeCard);
            InspectAuctionRequest request = new InspectAuctionRequest(AuctionInspectionResult.PASSED, null);

            // when
            InspectAuctionResponse response = service.inspectAuction(1L, 1L, request);

            // then
            assertThat(response.status()).isEqualTo(AuctionStatus.APPROVED);
            verify(eventPublisher).publishEvent(ArgumentMatchers.<Object>argThat(event -> {
                if (!(event instanceof AuctionInspectionPassedEvent passedEvent)) {
                    return false;
                }
                return passedEvent.getAuctionId().equals(1L)
                        && passedEvent.getSellerId().equals(2L)
                        && passedEvent.getAuctionTitle().equals(auction.getTitle())
                        && passedEvent.getApprovedAt() != null;
            }));
        }

        @Test
        @DisplayName("성공: FAILED (REJECTED) 사유 포함")
        void successRejected() {
            // given
            Auction auction = buildAuction(1L, 2L, AuctionStatus.PENDING);
            given(auctionRepository.findById(1L))
                    .willReturn(Optional.of(auction))
                    .willReturn(Optional.of(auction));
            InspectAuctionRequest request = new InspectAuctionRequest(AuctionInspectionResult.FAILED, "카드 상태 불량");

            // when
            InspectAuctionResponse response = service.inspectAuction(1L, 1L, request);

            // then
            assertThat(response.status()).isEqualTo(AuctionStatus.REJECTED);
            assertThat(response.reason()).isEqualTo("카드 상태 불량");
            verify(eventPublisher).publishEvent(ArgumentMatchers.<Object>argThat(event -> {
                if (!(event instanceof AuctionInspectionFailedEvent failedEvent)) {
                    return false;
                }
                return failedEvent.getAuctionId().equals(1L)
                        && failedEvent.getSellerId().equals(2L)
                        && failedEvent.getAuctionTitle().equals(auction.getTitle())
                        && failedEvent.getFailedReason().equals("카드 상태 불량");
            }));
        }

        @Test
        @DisplayName("실패: 경매 미존재 → AUCTION_NOT_FOUND")
        void failNotFound() {
            // given
            given(auctionRepository.findById(999L)).willReturn(Optional.empty());
            InspectAuctionRequest request = new InspectAuctionRequest(AuctionInspectionResult.PASSED, null);

            // when / then
            assertThatThrownBy(() -> service.inspectAuction(1L, 999L, request))
                    .isInstanceOf(AuctionException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.AUCTION_NOT_FOUND);
        }

        @Test
        @DisplayName("실패: 검수 불가 상태(ACTIVE) → AUCTION_NOT_INSPECTING")
        void failNotInspectable() {
            // given
            Auction auction = buildAuction(1L, 2L, AuctionStatus.ACTIVE);
            given(auctionRepository.findById(1L)).willReturn(Optional.of(auction));
            InspectAuctionRequest request = new InspectAuctionRequest(AuctionInspectionResult.PASSED, null);

            // when / then
            assertThatThrownBy(() -> service.inspectAuction(1L, 1L, request))
                    .isInstanceOf(AuctionException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.AUCTION_NOT_INSPECTING);
        }

        @Test
        @DisplayName("실패: 락 획득 실패 → AUCTION_LOCK_FAILED")
        void failLockFailed() throws InterruptedException {
            // given
            Auction auction = buildAuction(1L, 2L, AuctionStatus.PENDING);
            given(auctionRepository.findById(1L)).willReturn(Optional.of(auction));
            given(rLock.tryLock(anyLong(), any(TimeUnit.class))).willReturn(false);
            InspectAuctionRequest request = new InspectAuctionRequest(AuctionInspectionResult.PASSED, null);

            // when / then
            assertThatThrownBy(() -> service.inspectAuction(1L, 1L, request))
                    .isInstanceOf(AuctionException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.AUCTION_LOCK_FAILED);
        }

        @Test
        @DisplayName("실패: FAILED인데 사유 없음 → AUCTION_REASON_REQUIRED")
        void failNoReasonForRejection() {
            // given
            Auction auction = buildAuction(1L, 2L, AuctionStatus.PENDING);
            given(auctionRepository.findById(1L))
                    .willReturn(Optional.of(auction))
                    .willReturn(Optional.of(auction));
            InspectAuctionRequest request = new InspectAuctionRequest(AuctionInspectionResult.FAILED, "  ");

            // when / then
            assertThatThrownBy(() -> service.inspectAuction(1L, 1L, request))
                    .isInstanceOf(AuctionException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.AUCTION_REASON_REQUIRED);
        }
    }
}
