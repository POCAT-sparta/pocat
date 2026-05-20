package com.rocketcrew.pocat.domain.bid.entity;

import com.rocketcrew.pocat.domain.bid.enums.BidStatus;
import com.rocketcrew.pocat.global.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Builder
@AllArgsConstructor
@Entity
@Table(name = "auction_bids")
@SQLDelete(sql = "UPDATE auction_bids SET deleted_at = NOW() WHERE id = ?")
@SQLRestriction("deleted_at IS NULL")
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
