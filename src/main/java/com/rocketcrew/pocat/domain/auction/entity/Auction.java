package com.rocketcrew.pocat.domain.auction.entity;

import com.rocketcrew.pocat.domain.auction.enums.AuctionStatus;
import com.rocketcrew.pocat.global.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.SQLDelete;

import java.time.LocalDateTime;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
@Entity
@Table(name = "auctions")
@SQLDelete(sql = "UPDATE auctions SET deleted_at = NOW() WHERE id = ?")
public class Auction extends BaseEntity {

    @Column(name = "card_id", nullable = false)
    private Long cardId;

    @Column(name = "seller_id", nullable = false)
    private Long sellerId;

    @Column(name = "highest_bidder_id")
    private Long highestBidderId;

    @Column(name = "title", nullable = false)
    private String title;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "card_image_url", length = 500)
    private String cardImageUrl;

    @Column(name = "starting_price", nullable = false)
    private Long startingPrice;

    @Column(name = "buyout_price")
    private Long buyoutPrice;

    @Column(name = "highest_price")
    private Long highestPrice;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private AuctionStatus status;

    @Column(name = "started_at")
    private LocalDateTime startedAt;

    @Column(name = "ended_at")
    private LocalDateTime endedAt;

    @Column(name = "cancel_reason", columnDefinition = "TEXT")
    private String cancelReason;

    public void cancel(String cancelReason) {
        this.status = AuctionStatus.CANCELLED;
        this.cancelReason = cancelReason;
    }
}
