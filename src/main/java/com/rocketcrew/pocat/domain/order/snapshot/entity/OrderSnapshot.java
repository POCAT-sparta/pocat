package com.rocketcrew.pocat.domain.order.snapshot.entity;

import com.rocketcrew.pocat.global.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.SQLDelete;

import java.math.BigDecimal;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Builder
@AllArgsConstructor
@Entity
@Table(name = "order_snapshots")
@SQLDelete(sql = "UPDATE order_snapshots SET deleted_at = NOW() WHERE id = ?")
public class OrderSnapshot extends BaseEntity {

    @Column(name = "order_id", nullable = false)
    private Long orderId;

    @Column(name = "final_price", nullable = false, precision = 12, scale = 2)
    private BigDecimal finalPrice;

    @Column(name = "fee_rate", nullable = false, precision = 5, scale = 2)
    private BigDecimal feeRate;

    @Column(name = "fee", nullable = false, precision = 12, scale = 2)
    private BigDecimal fee;

    @Column(name = "seller_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal sellerAmount;

    @Column(name = "snapshot_json", columnDefinition = "TEXT")
    private String snapshotJson;
}
