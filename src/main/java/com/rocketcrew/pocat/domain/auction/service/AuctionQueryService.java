package com.rocketcrew.pocat.domain.auction.service;

import com.rocketcrew.pocat.domain.auction.dto.request.AuctionSearchCondition;
import com.rocketcrew.pocat.domain.auction.dto.response.AdminAuctionResponse;
import com.rocketcrew.pocat.domain.auction.dto.response.AuctionResponse;
import com.rocketcrew.pocat.domain.auction.dto.response.SearchAuctionResponse;
import com.rocketcrew.pocat.domain.auction.entity.Auction;
import com.rocketcrew.pocat.domain.auction.enums.AuctionStatus;
import com.rocketcrew.pocat.domain.auction.repository.AuctionRepository;
import com.rocketcrew.pocat.domain.card.entity.Card;
import com.rocketcrew.pocat.domain.card.entity.enums.CardCategory;
import com.rocketcrew.pocat.domain.card.entity.enums.CardGrade;
import com.rocketcrew.pocat.domain.card.service.CardQueryService;
import com.rocketcrew.pocat.domain.like.service.LikeQueryService;
import com.rocketcrew.pocat.domain.user.entity.User;
import com.rocketcrew.pocat.domain.user.service.UserQueryService;
import com.rocketcrew.pocat.global.exception.common.ErrorCode;
import com.rocketcrew.pocat.global.exception.domain.AuctionException;
import com.rocketcrew.pocat.global.exception.domain.CardException;
import com.rocketcrew.pocat.global.exception.domain.UserException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AuctionQueryService {

    private static final Set<AuctionStatus> PUBLIC_DETAIL_STATUSES = Set.of(
            AuctionStatus.ACTIVE,
            AuctionStatus.ENDED,
            AuctionStatus.NO_BIDDER
    );
    private static final Set<AuctionStatus> PUBLIC_LIST_STATUSES = Set.copyOf(List.of(
            AuctionStatus.ACTIVE,
            AuctionStatus.ENDED,
            AuctionStatus.NO_BIDDER
    ));

    private final AuctionRepository auctionRepository;
    private final CardQueryService cardQueryService;
    private final UserQueryService userQueryService;
    private final LikeQueryService likeQueryService;

    public Page<SearchAuctionResponse> getAuctions(
            String keyword,
            String series,
            String setName,
            CardGrade grade,
            CardCategory category,
            AuctionStatus status,
            Pageable pageable
    ) {
        validatePublicListStatus(status);
        AuctionSearchCondition condition = new AuctionSearchCondition(
                keyword, series, setName, grade, category, status);
        return auctionRepository.searchAuctions(condition, pageable);
    }

    public Page<AdminAuctionResponse> getAdminAuctions(
            String keyword,
            String series,
            String setName,
            CardGrade grade,
            CardCategory category,
            AuctionStatus status,
            Pageable pageable
    ) {
        AuctionSearchCondition condition = new AuctionSearchCondition(
                keyword, series, setName, grade, category, status);
        return auctionRepository.searchAdminAuctions(condition, pageable);
    }

    public Page<SearchAuctionResponse> getMyAuctions(Long sellerId, AuctionStatus status, Pageable pageable) {
        return auctionRepository.searchMyAuctions(sellerId, status, pageable);
    }

    public AuctionResponse getAuction(Long id, Long userId) {
        Auction auction = findAuctionEntityOrThrow(id);
        if (!canViewAuctionDetail(auction, userId)) {
            throw new AuctionException(ErrorCode.AUCTION_NOT_FOUND);
        }
        Card card = getAuctionCard(auction.getCardId());
        User seller = getAuctionSeller(auction.getSellerId());
        User highestBidder = auction.getHighestBidderId() == null
                ? null
                : getAuctionHighestBidder(auction.getHighestBidderId());
        long likeCount = likeQueryService.countByAuctionId(auction.getId());
        boolean isLiked = userId != null && likeQueryService.existsByUserIdAndAuctionId(userId, auction.getId());

        return AuctionResponse.of(auction, seller, card, highestBidder, likeCount, isLiked);
    }

    private boolean canViewAuctionDetail(Auction auction, Long userId) {
        return PUBLIC_DETAIL_STATUSES.contains(auction.getStatus())
                || auction.getSellerId().equals(userId);
    }

    private void validatePublicListStatus(AuctionStatus status) {
        if (status != null && !PUBLIC_LIST_STATUSES.contains(status)) {
            throw new AuctionException(ErrorCode.INVALID_INPUT);
        }
    }

    public Auction findAuctionEntityOrThrow(Long id) {
        return auctionRepository.findById(id)
                .orElseThrow(() -> new AuctionException(ErrorCode.AUCTION_NOT_FOUND));
    }

    private Card getAuctionCard(Long cardId) {
        try {
            return cardQueryService.getCardEntity(cardId);
        } catch (CardException e) {
            throw new AuctionException(ErrorCode.AUCTION_CARD_NOT_FOUND);
        }
    }

    private User getAuctionSeller(Long sellerId) {
        try {
            return userQueryService.getUserEntity(sellerId);
        } catch (UserException e) {
            throw new AuctionException(ErrorCode.AUCTION_SELLER_NOT_FOUND);
        }
    }

    private User getAuctionHighestBidder(Long highestBidderId) {
        try {
            return userQueryService.getUserEntity(highestBidderId);
        } catch (UserException e) {
            throw new AuctionException(ErrorCode.AUCTION_HIGHEST_BIDDER_NOT_FOUND);
        }
    }
}
