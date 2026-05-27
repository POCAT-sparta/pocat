package com.rocketcrew.pocat.domain.auction.entity;

import com.rocketcrew.pocat.domain.auction.enums.AuctionStatus;
import com.rocketcrew.pocat.global.entity.BaseEntity;
import com.rocketcrew.pocat.global.exception.common.ErrorCode;
import com.rocketcrew.pocat.global.exception.domain.AuctionException;
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
        this.startedAt = null;
        this.endedAt = null;
        this.reason = null;
    }

    public void reject(Long inspectedBy, LocalDateTime inspectedAt, String reason) {
        this.status = AuctionStatus.REJECTED;
        this.inspectedBy = inspectedBy;
        this.inspectedAt = inspectedAt;
        this.reason = reason;
        this.startedAt = null;
        this.endedAt = null;
    }

    // 승인된 경매를 실제 진행 상태로 전환하고 시작/종료 시각을 확정한다.
    public void activate(LocalDateTime startedAt, LocalDateTime endedAt) {
        validateApproved();
        validateAuctionPeriod(startedAt, endedAt);
        this.status = AuctionStatus.ACTIVE;
        this.startedAt = startedAt;
        this.endedAt = endedAt;
        this.reason = null;
    }

    // 입찰자가 있는 경매를 정상 종료 상태로 전환한다.
    public void end() {
        validateActive();
        this.status = AuctionStatus.ENDED;
    }

    // 입찰자가 없는 경매를 유찰 상태로 전환한다.
    public void markNoBidder() {
        validateActive();
        this.status = AuctionStatus.NO_BIDDER;
    }

    public void updateHighestBid(Long highestPrice, Long highestBidderId) {
        this.highestPrice = highestPrice;
        this.highestBidderId = highestBidderId;
    }

    // 검수 승인 상태인 경매만 ACTIVE 상태로 전환할 수 있도록 보장한다.
    private void validateApproved() {
        if (this.status != AuctionStatus.APPROVED) {
            throw new AuctionException(ErrorCode.AUCTION_INVALID_STATUS_TRANSITION);
        }
    }

    // 진행 중인 경매만 종료 또는 유찰 상태로 전환할 수 있도록 보장한다.
    private void validateActive() {
        if (this.status != AuctionStatus.ACTIVE) {
            throw new AuctionException(ErrorCode.AUCTION_NOT_ACTIVE);
        }
    }

    // 경매 시작/종료 시각이 비어 있거나 순서가 잘못되지 않았는지 확인한다.
    private void validateAuctionPeriod(LocalDateTime startedAt, LocalDateTime endedAt) {
        if (startedAt == null || endedAt == null || !endedAt.isAfter(startedAt)) {
            throw new AuctionException(ErrorCode.INVALID_INPUT);
        }
    }
}
