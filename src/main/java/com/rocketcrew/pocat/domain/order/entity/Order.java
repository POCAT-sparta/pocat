package com.rocketcrew.pocat.domain.order.entity;

import com.rocketcrew.pocat.domain.order.enums.DeliveryStatus;
import com.rocketcrew.pocat.domain.order.enums.OrderStatus;
import com.rocketcrew.pocat.global.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.SQLDelete;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Builder
@AllArgsConstructor
@Entity
@Table(name = "orders")
@SQLDelete(sql = "UPDATE orders SET deleted_at = NOW() WHERE id = ?")
public class Order extends BaseEntity {

    @Column(name = "auction_id")
    private Long auctionId;

    @Column(name = "card_id", nullable = false)
    private Long cardId;

    @Column(name = "seller_id")
    private Long sellerId;

    @Column(name = "buyer_id", nullable = false)
    private Long buyerId;

    @Column(name = "order_uid", nullable = false, length = 50, unique = true)
    private String orderUid;

    @Column(name = "final_price", nullable = false)
    private Long finalPrice;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private OrderStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "delivery_status", length = 20)
    private DeliveryStatus deliveryStatus;

    @Column(name = "cancel_reason", length = 255)
    private String cancelReason;

    public void cancel(String reason) {
        this.status = OrderStatus.CANCELLED;
        this.cancelReason = reason;
        if (this.deliveryStatus != null) {
            this.deliveryStatus = DeliveryStatus.CANCELLED;
        }
    }

    public void refund() {
        this.status = OrderStatus.REFUNDED;
    }
}
