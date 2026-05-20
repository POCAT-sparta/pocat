package com.rocketcrew.pocat.domain.order.snapshot.entity;

import com.rocketcrew.pocat.global.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.SQLDelete;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Builder
@AllArgsConstructor
@Entity
@Table(name = "order_snapshots")
@SQLDelete(sql = "UPDATE order_snapshots SET deleted_at = NOW() WHERE id = ?")
public class OrderSnapshot extends BaseEntity {

    @Column(name = "order_uid", nullable = false, length = 50, unique = true)
    private String orderUid;

    @Column(name = "final_price", nullable = false)
    private Long finalPrice;

    @Column(name = "fee_rate", nullable = false)
    private Long feeRate;

    @Column(name = "fee", nullable = false)
    private Long fee;

    @Column(name = "seller_amount", nullable = false)
    private Long sellerAmount;

    @Column(name = "snapshot_json", columnDefinition = "TEXT")
    private String snapshotJson;
}
