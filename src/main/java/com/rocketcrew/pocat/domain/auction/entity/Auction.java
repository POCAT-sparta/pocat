package com.rocketcrew.pocat.domain.auction.entity;

import com.rocketcrew.pocat.global.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
@Entity
@Table(name = "auctions")
public class Auction extends BaseEntity {

    @Column(name = "card_id", nullable = false)
    private Long cardId;

    @Column(nullable = false)
    private Long sellerId;

    private Long highestBidderId;

    @Column(nullable = false)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "card_image_url", length = 500)
    private String cardImageUrl;

    @Column(nullable = false)
    private Long startingPrice;

    private Long buyoutPrice;

    private Long highestPrice;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private AuctionStatus status;

    private LocalDateTime startedAt;

    private LocalDateTime endedAt;

    @Column(name = "cancel_reason", columnDefinition = "TEXT")
    private String cancelReason;

    public void cancel(String cancelReason) {
        this.status = AuctionStatus.CANCELLED;
        this.cancelReason = cancelReason;
    }
}
