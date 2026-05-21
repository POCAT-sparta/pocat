package com.rocketcrew.pocat.domain.auction.dto.response;

import com.rocketcrew.pocat.domain.auction.entity.Auction;
import com.rocketcrew.pocat.domain.auction.enums.AuctionStatus;
import com.rocketcrew.pocat.domain.card.entity.Card;
import com.rocketcrew.pocat.domain.card.entity.enums.CardGrade;
import com.rocketcrew.pocat.domain.user.entity.User;

import java.time.LocalDateTime;

public record AuctionResponse(
        Long id,
        Long sellerId,
        String sellerNickname,
        String title,
        String description,
        Long cardId,
        String cardName,
        CardGrade grade,
        String cardImageUrl,
        Long startingPrice,
        Long buyoutPrice,
        Long highestPrice,
        Long highestBidderId,
        String highestBidderNickname,
        AuctionStatus status,
        LocalDateTime startedAt,
        LocalDateTime endedAt,
        long likeCount,
        boolean isLiked
) {
    public static AuctionResponse of(
            Auction auction,
            User seller,
            Card card,
            User highestBidder,
            long likeCount,
            boolean isLiked
    ) {
        return new AuctionResponse(
                auction.getId(),
                auction.getSellerId(),
                seller.getNickname(),
                auction.getTitle(),
                auction.getDescription(),
                auction.getCardId(),
                card.getName(),
                card.getGrade(),
                auction.getCardImageUrl(),
                auction.getStartingPrice(),
                auction.getBuyoutPrice(),
                auction.getHighestPrice(),
                auction.getHighestBidderId(),
                highestBidder == null ? null : highestBidder.getNickname(),
                auction.getStatus(),
                auction.getStartedAt(),
                auction.getEndedAt(),
                likeCount,
                isLiked
        );
    }
}
