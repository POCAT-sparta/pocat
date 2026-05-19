package com.rocketcrew.pocat.domain.settlement.entity;

import com.rocketcrew.pocat.global.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.SQLDelete;

import java.time.LocalDateTime;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Builder
@AllArgsConstructor
@Entity
@Table(name = "settlements")
@SQLDelete(sql = "UPDATE settlements SET deleted_at = NOW() WHERE id = ?")
public class Settlement extends BaseEntity {

    @Column(name = "order_id", nullable = false)
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

    @Column(name = "settled_at", nullable = false)
    private LocalDateTime settledAt;
}
