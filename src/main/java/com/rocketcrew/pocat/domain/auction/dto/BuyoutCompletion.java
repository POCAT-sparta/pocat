package com.rocketcrew.pocat.domain.auction.dto;

import com.rocketcrew.pocat.domain.auction.entity.Auction;
import com.rocketcrew.pocat.domain.bid.entity.AuctionBid;

public record BuyoutCompletion(Auction auction, AuctionBid buyoutBid) {
}
