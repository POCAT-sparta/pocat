package com.rocketcrew.pocat.domain.auction.entity;

import com.rocketcrew.pocat.domain.auction.enums.AuctionStatus;
import com.rocketcrew.pocat.global.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;

import java.time.LocalDateTime;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
@Entity
@Table(name = "auctions")
@SQLDelete(sql = "UPDATE auctions SET deleted_at = NOW() WHERE id = ?")
@SQLRestriction("deleted_at IS NULL")
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

    @Column(name = "reason", columnDefinition = "TEXT")
    private String reason;

    @Column(name = "inspected_at")
    private LocalDateTime inspectedAt;

    @Column(name = "inspected_by")
    private Long inspectedBy;

    public void update(String title, String description, Long startingPrice, Long buyoutPrice) {
        if (title != null) {
            this.title = title;
        }
        if (description != null) {
            this.description = description;
        }
        if (startingPrice != null) {
            this.startingPrice = startingPrice;
        }
        if (buyoutPrice != null) {
            this.buyoutPrice = buyoutPrice;
        }
    }

    public void cancel(String reason) {
        this.status = AuctionStatus.CANCELLED;
        this.reason = reason;
    }

    public void cancelByAdmin(String reason) {
        this.status = AuctionStatus.CANCELLED;
        this.reason = reason;
    }

    public void approve(Long inspectedBy, LocalDateTime inspectedAt) {
        this.status = AuctionStatus.APPROVED;
        this.inspectedBy = inspectedBy;
        this.inspectedAt = inspectedAt;
        this.reason = null;
    }

    public void reject(String reason) {
        this.status = AuctionStatus.REJECTED;
        this.reason = reason;
    }

    public void updateHighestBid(Long highestPrice, Long highestBidderId) {
        this.highestPrice = highestPrice;
        this.highestBidderId = highestBidderId;
    }
}
