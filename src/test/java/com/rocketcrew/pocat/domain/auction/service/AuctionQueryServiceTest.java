package com.rocketcrew.pocat.domain.auction.service;

import com.rocketcrew.pocat.domain.auction.dto.response.AdminAuctionResponse;
import com.rocketcrew.pocat.domain.auction.dto.response.AuctionResponse;
import com.rocketcrew.pocat.domain.auction.dto.response.SearchAuctionResponse;
import com.rocketcrew.pocat.domain.auction.entity.Auction;
import com.rocketcrew.pocat.domain.auction.enums.AuctionStatus;
import com.rocketcrew.pocat.domain.auction.repository.AuctionRepository;
import com.rocketcrew.pocat.domain.card.entity.Card;
import com.rocketcrew.pocat.domain.card.entity.enums.CardCategory;
import com.rocketcrew.pocat.domain.card.entity.enums.CardGrade;
import com.rocketcrew.pocat.domain.card.entity.enums.CardSource;
import com.rocketcrew.pocat.domain.card.entity.enums.CardStatus;
import com.rocketcrew.pocat.domain.card.service.CardQueryService;
import com.rocketcrew.pocat.domain.like.service.LikeQueryService;
import com.rocketcrew.pocat.domain.user.entity.User;
import com.rocketcrew.pocat.domain.user.enums.UserRole;
import com.rocketcrew.pocat.domain.user.service.UserQueryService;
import com.rocketcrew.pocat.global.exception.common.ErrorCode;
import com.rocketcrew.pocat.global.exception.domain.AuctionException;
import com.rocketcrew.pocat.global.exception.domain.CardException;
import com.rocketcrew.pocat.global.exception.domain.UserException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AuctionQueryServiceTest {

    @InjectMocks
    AuctionQueryService service;

    @Mock
    AuctionRepository auctionRepository;

    @Mock
    CardQueryService cardQueryService;

    @Mock
    UserQueryService userQueryService;

    @Mock
    LikeQueryService likeQueryService;

    private Auction activeAuction;
    private Card card;
    private User seller;

    @BeforeEach
    void setUp() {
        seller = User.builder()
                .email("seller@test.com")
                .password("pw")
                .nickname("판매자")
                .userRole(UserRole.USER)
                .billingKey("bkey")
                .build();
        ReflectionTestUtils.setField(seller, "id", 2L);

        card = Card.builder()
                .userId(2L)
                .name("리자몽")
                .series("SV")
                .setId("sv2")
                .setName("팔데아의 진화")
                .cardNumber("006")
                .rarity("SR")
                .category(CardCategory.POKEMON)
                .grade(CardGrade.PSA_10)
                .source(CardSource.TCGDEX)
                .status(CardStatus.ACTIVE)
                .build();
        ReflectionTestUtils.setField(card, "id", 1L);

        activeAuction = Auction.builder()
                .cardId(1L)
                .sellerId(2L)
                .title("리자몽 경매")
                .description("리자몽 PSA10")
                .startingPrice(10000L)
                .buyoutPrice(100000L)
                .status(AuctionStatus.ACTIVE)
                .startedAt(LocalDateTime.now().minusHours(1))
                .endedAt(LocalDateTime.now().plusHours(1))
                .build();
        ReflectionTestUtils.setField(activeAuction, "id", 1L);
    }

    // ---------------------------------------------------------------
    // getAuctions
    // ---------------------------------------------------------------
    @Nested
    @DisplayName("getAuctions (공개 목록)")
    class GetAuctions {

        @Test
        @DisplayName("성공: 검색 조건으로 ACTIVE 경매 목록 반환")
        void success() {
            // given
            Pageable pageable = PageRequest.of(0, 20);
            SearchAuctionResponse resp = new SearchAuctionResponse(
                    1L, 2L, "판매자", "리자몽 경매", 1L, "리자몽",
                    CardGrade.PSA_10, null, 10000L, null, 100000L,
                    AuctionStatus.ACTIVE, null, null, null, 0L);
            Page<SearchAuctionResponse> page = new PageImpl<>(List.of(resp), pageable, 1);
            given(auctionRepository.searchAuctions(any(), eq(pageable))).willReturn(page);

            // when
            Page<SearchAuctionResponse> result = service.getAuctions(null, null, null, null, null, AuctionStatus.ACTIVE, pageable);

            // then
            assertThat(result.getTotalElements()).isEqualTo(1);
            assertThat(result.getContent()).hasSize(1);
        }

        @Test
        @DisplayName("성공: 검색 결과 없으면 빈 페이지 반환")
        void successEmpty() {
            // given
            Pageable pageable = PageRequest.of(0, 20);
            Page<SearchAuctionResponse> empty = new PageImpl<>(Collections.emptyList(), pageable, 0);
            given(auctionRepository.searchAuctions(any(), eq(pageable))).willReturn(empty);

            // when
            Page<SearchAuctionResponse> result = service.getAuctions("없는키워드", null, null, null, null, AuctionStatus.ACTIVE, pageable);

            // then
            assertThat(result.getTotalElements()).isZero();
        }
    }

    // ---------------------------------------------------------------
    // getAdminAuctions
    // ---------------------------------------------------------------
    @Nested
    @DisplayName("getAdminAuctions (관리자 목록)")
    class GetAdminAuctions {

        @Test
        @DisplayName("성공: 전체 상태 경매 목록 반환")
        void success() {
            // given
            Pageable pageable = PageRequest.of(0, 20);
            AdminAuctionResponse resp = new AdminAuctionResponse(
                    1L, 2L, "판매자", "리자몽 경매", 1L, "리자몽",
                    CardGrade.PSA_10, null, 10000L, null, 100000L,
                    AuctionStatus.PENDING, null, null, 0L);
            Page<AdminAuctionResponse> page = new PageImpl<>(List.of(resp), pageable, 1);
            given(auctionRepository.searchAdminAuctions(any(), eq(pageable))).willReturn(page);

            // when
            Page<AdminAuctionResponse> result = service.getAdminAuctions(null, null, null, null, null, null, pageable);

            // then
            assertThat(result.getTotalElements()).isEqualTo(1);
        }

        @Test
        @DisplayName("성공: 상태 필터 적용 가능")
        void successWithStatusFilter() {
            // given
            Pageable pageable = PageRequest.of(0, 20);
            Page<AdminAuctionResponse> empty = new PageImpl<>(Collections.emptyList(), pageable, 0);
            given(auctionRepository.searchAdminAuctions(any(), eq(pageable))).willReturn(empty);

            // when
            Page<AdminAuctionResponse> result = service.getAdminAuctions(null, null, null, null, null, AuctionStatus.INSPECTING, pageable);

            // then
            assertThat(result).isNotNull();
        }
    }

    // ---------------------------------------------------------------
    // getMyAuctions
    // ---------------------------------------------------------------
    @Nested
    @DisplayName("getMyAuctions (판매자 목록)")
    class GetMyAuctions {

        @Test
        @DisplayName("성공: 내 경매 목록 반환")
        void success() {
            // given
            Pageable pageable = PageRequest.of(0, 20);
            SearchAuctionResponse resp = new SearchAuctionResponse(
                    1L, 2L, "판매자", "리자몽 경매", 1L, "리자몽",
                    CardGrade.PSA_10, null, 10000L, null, 100000L,
                    AuctionStatus.ACTIVE, null, null, null, 0L);
            Page<SearchAuctionResponse> page = new PageImpl<>(List.of(resp), pageable, 1);
            given(auctionRepository.searchMyAuctions(eq(2L), any(), eq(pageable))).willReturn(page);

            // when
            Page<SearchAuctionResponse> result = service.getMyAuctions(2L, null, pageable);

            // then
            assertThat(result.getTotalElements()).isEqualTo(1);
        }

        @Test
        @DisplayName("성공: 상태 필터 포함 내 경매 목록 반환")
        void successWithStatus() {
            // given
            Pageable pageable = PageRequest.of(0, 20);
            Page<SearchAuctionResponse> empty = new PageImpl<>(Collections.emptyList(), pageable, 0);
            given(auctionRepository.searchMyAuctions(eq(2L), eq(AuctionStatus.ENDED), eq(pageable))).willReturn(empty);

            // when
            Page<SearchAuctionResponse> result = service.getMyAuctions(2L, AuctionStatus.ENDED, pageable);

            // then
            assertThat(result).isNotNull();
        }
    }

    // ---------------------------------------------------------------
    // getAuction (단건 조회)
    // ---------------------------------------------------------------
    @Nested
    @DisplayName("getAuction (단건 조회)")
    class GetAuction {

        @Test
        @DisplayName("성공: ACTIVE 경매 상세 조회 (비로그인)")
        void successPublicView() {
            // given
            given(auctionRepository.findById(1L)).willReturn(Optional.of(activeAuction));
            given(cardQueryService.getCardEntity(1L)).willReturn(card);
            given(userQueryService.getUserEntity(2L)).willReturn(seller);
            given(likeQueryService.countByAuctionId(1L)).willReturn(5L);
            given(likeQueryService.existsByUserIdAndAuctionId(anyLong(), anyLong())).willReturn(false);

            // when
            AuctionResponse response = service.getAuction(1L, null);

            // then
            assertThat(response.auctionId()).isEqualTo(1L);
            assertThat(response.title()).isEqualTo("리자몽 경매");
            assertThat(response.likeCount()).isEqualTo(5L);
        }

        @Test
        @DisplayName("성공: 판매자는 PENDING 상태 경매도 조회 가능")
        void successSellerViewsPending() {
            // given
            Auction pendingAuction = Auction.builder()
                    .cardId(1L).sellerId(2L).title("PENDING경매")
                    .startingPrice(1000L).buyoutPrice(10000L)
                    .status(AuctionStatus.PENDING).build();
            ReflectionTestUtils.setField(pendingAuction, "id", 2L);
            given(auctionRepository.findById(2L)).willReturn(Optional.of(pendingAuction));
            given(cardQueryService.getCardEntity(1L)).willReturn(card);
            given(userQueryService.getUserEntity(2L)).willReturn(seller);
            given(likeQueryService.countByAuctionId(2L)).willReturn(0L);
            given(likeQueryService.existsByUserIdAndAuctionId(2L, 2L)).willReturn(false);

            // when
            AuctionResponse response = service.getAuction(2L, 2L);  // userId == sellerId

            // then
            assertThat(response.status()).isEqualTo(AuctionStatus.PENDING);
        }

        @Test
        @DisplayName("실패: 존재하지 않는 경매 → AUCTION_NOT_FOUND")
        void failNotFound() {
            // given
            given(auctionRepository.findById(999L)).willReturn(Optional.empty());

            // when / then
            assertThatThrownBy(() -> service.getAuction(999L, null))
                    .isInstanceOf(AuctionException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.AUCTION_NOT_FOUND);
        }

        @Test
        @DisplayName("실패: 비로그인 유저가 PENDING 경매 조회 → AUCTION_NOT_FOUND")
        void failPendingNotVisible() {
            // given
            Auction pendingAuction = Auction.builder()
                    .cardId(1L).sellerId(2L).title("PENDING경매")
                    .startingPrice(1000L).buyoutPrice(10000L)
                    .status(AuctionStatus.PENDING).build();
            ReflectionTestUtils.setField(pendingAuction, "id", 2L);
            given(auctionRepository.findById(2L)).willReturn(Optional.of(pendingAuction));

            // when / then
            assertThatThrownBy(() -> service.getAuction(2L, null))
                    .isInstanceOf(AuctionException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.AUCTION_NOT_FOUND);
        }

        @Test
        @DisplayName("실패: 카드 미존재 → AUCTION_CARD_NOT_FOUND")
        void failCardNotFound() {
            // given
            given(auctionRepository.findById(1L)).willReturn(Optional.of(activeAuction));
            given(cardQueryService.getCardEntity(1L))
                    .willThrow(new CardException(ErrorCode.CARD_NOT_FOUND));

            // when / then
            assertThatThrownBy(() -> service.getAuction(1L, null))
                    .isInstanceOf(AuctionException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.AUCTION_CARD_NOT_FOUND);
        }

        @Test
        @DisplayName("실패: 판매자 미존재 → AUCTION_SELLER_NOT_FOUND")
        void failSellerNotFound() {
            // given
            given(auctionRepository.findById(1L)).willReturn(Optional.of(activeAuction));
            given(cardQueryService.getCardEntity(1L)).willReturn(card);
            given(userQueryService.getUserEntity(2L))
                    .willThrow(new UserException(ErrorCode.USER_NOT_FOUND));

            // when / then
            assertThatThrownBy(() -> service.getAuction(1L, null))
                    .isInstanceOf(AuctionException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.AUCTION_SELLER_NOT_FOUND);
        }
    }

    // ---------------------------------------------------------------
    // findAuctionEntityOrThrow
    // ---------------------------------------------------------------
    @Nested
    @DisplayName("findAuctionEntityOrThrow")
    class FindAuctionEntityOrThrow {

        @Test
        @DisplayName("성공: 경매 엔티티 반환")
        void success() {
            // given
            given(auctionRepository.findById(1L)).willReturn(Optional.of(activeAuction));

            // when
            Auction result = service.findAuctionEntityOrThrow(1L);

            // then
            assertThat(result.getId()).isEqualTo(1L);
        }

        @Test
        @DisplayName("실패: 경매 미존재 → AUCTION_NOT_FOUND")
        void fail() {
            // given
            given(auctionRepository.findById(999L)).willReturn(Optional.empty());

            // when / then
            assertThatThrownBy(() -> service.findAuctionEntityOrThrow(999L))
                    .isInstanceOf(AuctionException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.AUCTION_NOT_FOUND);
        }
    }
}
