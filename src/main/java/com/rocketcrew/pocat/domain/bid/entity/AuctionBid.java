package com.rocketcrew.pocat.domain.bid.entity;

import com.rocketcrew.pocat.domain.bid.enums.BidStatus;
import com.rocketcrew.pocat.global.entity.BaseEntity;
import com.rocketcrew.pocat.global.exception.common.ErrorCode;
import com.rocketcrew.pocat.global.exception.domain.BidException;
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

    public void markOutbid() {
        validateLeading();
        this.status = BidStatus.OUTBID;
    }

    public void cancel() {
        validateCancellable();
        this.status = BidStatus.CANCELLED;
    }

    // 경매 종료 시 최종 최고 입찰을 낙찰 상태로 전환한다.
    public void markWon() {
        validateLeading();
        this.status = BidStatus.WON;
    }

    // 경매 종료 시 낙찰되지 않은 입찰을 패찰 상태로 전환한다.
    public void markLost() {
        if (this.status == BidStatus.LOST) {
            return;
        }
        validateLosable();
        this.status = BidStatus.LOST;
    }

    // 현재 최고 입찰 상태에서만 갱신/낙찰 상태로 전환할 수 있도록 보장한다.
    private void validateLeading() {
        if (this.status != BidStatus.LEADING) {
            throw new BidException(ErrorCode.BID_INVALID_STATUS_TRANSITION);
        }
    }

    // 아직 최종 결과가 확정되지 않은 입찰만 취소할 수 있도록 보장한다.
    private void validateCancellable() {
        if (this.status == BidStatus.WON
                || this.status == BidStatus.LOST
                || this.status == BidStatus.CANCELLED) {
            throw new BidException(ErrorCode.BID_INVALID_STATUS_TRANSITION);
        }
    }

    // 이미 최고가 갱신으로 밀려난 입찰만 패찰 상태로 전환할 수 있도록 보장한다.
    private void validateLosable() {
        if (this.status != BidStatus.OUTBID) {
            throw new BidException(ErrorCode.BID_INVALID_STATUS_TRANSITION);
        }
    }
}
