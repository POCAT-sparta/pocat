package com.rocketcrew.pocat.domain.auction.dto.response;

import com.rocketcrew.pocat.domain.auction.entity.Auction;
import com.rocketcrew.pocat.domain.bid.entity.AuctionBid;
import com.rocketcrew.pocat.domain.order.entity.Order;
import com.rocketcrew.pocat.domain.payment.dto.response.PaymentResponse;

import java.time.LocalDateTime;

public record BuyoutAuctionResponse(
        Long auctionId,
        Long bidId,
        String orderUid,
        String orderStatus,
        String paymentUid,
        String paymentStatus,
        Long paidAmount,
        String auctionStatus,
        LocalDateTime purchasedAt
) {

    public static BuyoutAuctionResponse of(Auction auction, AuctionBid bid, Order order, PaymentResponse payment) {
        return new BuyoutAuctionResponse(
                auction.getId(),
                bid == null ? null : bid.getId(),
                order.getOrderUid(),
                order.getStatus().name(),
                payment == null ? null : payment.paymentUid(),
                payment == null || payment.status() == null ? null : payment.status().name(),
                order.getFinalPrice(),
                auction.getStatus().name(),
                bid == null ? null : bid.getCreatedAt()
        );
    }

    public static BuyoutAuctionResponse of(Auction auction, AuctionBid bid, Order order) {
        return of(auction, bid, order, null);
    }
}
