package com.rocketcrew.pocat.domain.auction.dto;

public record BuyoutReservation(
        Long auctionId,
        Long cardId,
        Long sellerId,
        Long buyoutPrice
) {
}
