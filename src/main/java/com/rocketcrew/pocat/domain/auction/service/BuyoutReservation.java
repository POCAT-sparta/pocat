package com.rocketcrew.pocat.domain.auction.service;

public record BuyoutReservation(
        Long auctionId,
        Long cardId,
        Long sellerId,
        Long buyoutPrice
) {
}
