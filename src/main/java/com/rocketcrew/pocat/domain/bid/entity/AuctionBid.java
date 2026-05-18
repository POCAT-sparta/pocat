package com.rocketcrew.pocat.domain.bid.entity;

import com.rocketcrew.pocat.domain.bid.enums.BidStatus;
import com.rocketcrew.pocat.global.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Builder
@AllArgsConstructor
@Entity
@Table(name = "auction_bids")
public class AuctionBid extends BaseEntity {

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "auction_id", nullable = false)
    private Long auctionId;

    @Column(name = "bid_price", nullable = false)
    private Long bidPrice;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private BidStatus status;
}
