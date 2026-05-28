package com.rocketcrew.pocat.domain.settlement.entity;

import com.rocketcrew.pocat.domain.settlement.enums.SettlementStatus;
import com.rocketcrew.pocat.global.entity.BaseEntity;
import com.rocketcrew.pocat.global.exception.common.ErrorCode;
import com.rocketcrew.pocat.global.exception.domain.SettlementException;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;

import java.time.LocalDateTime;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Builder
@AllArgsConstructor
@Entity
@Table(name = "settlements")
@SQLDelete(sql = "UPDATE settlements SET deleted_at = NOW() WHERE id = ?")
@SQLRestriction("deleted_at IS NULL")
public class Settlement extends BaseEntity {

    @Column(name = "settlement_uid", nullable = false, length = 50, unique = true)
    private String settlementUid;

    @Column(name = "order_id", nullable = false, unique = true)
    private Long orderId;

    @Column(name = "seller_id")
    private Long sellerId;

    @Column(name = "total_price", nullable = false)
    private Long totalPrice;

    @Column(name = "platform_fee", nullable = false)
    private Long platformFee;

    @Column(name = "seller_amount", nullable = false)
    private Long sellerAmount;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private SettlementStatus status;

    @Column(name = "settled_at")
    private LocalDateTime settledAt;

    public void complete() {
        if (this.status != SettlementStatus.PENDING) {
            throw new SettlementException(ErrorCode.SETTLEMENT_CANNOT_COMPLETE);
        }
        this.status = SettlementStatus.COMPLETED;
        this.settledAt = LocalDateTime.now();
    }

    public void refund() {
        if (this.status == SettlementStatus.REFUNDED) {
            return; // 멱등: 이미 환불된 정산은 스킵
        }
        if (this.status != SettlementStatus.PENDING && this.status != SettlementStatus.COMPLETED) {
            throw new SettlementException(ErrorCode.SETTLEMENT_CANNOT_REFUND);
        }
        this.status = SettlementStatus.REFUNDED;
    }
}
