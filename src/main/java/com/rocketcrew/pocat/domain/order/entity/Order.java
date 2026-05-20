package com.rocketcrew.pocat.domain.order.entity;

import com.rocketcrew.pocat.domain.order.enums.DeliveryStatus;
import com.rocketcrew.pocat.domain.order.enums.OrderStatus;
import com.rocketcrew.pocat.global.entity.BaseEntity;
import com.rocketcrew.pocat.global.exception.common.ErrorCode;
import com.rocketcrew.pocat.global.exception.domain.OrderException;
import com.rocketcrew.pocat.global.util.TsidGenerator;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Builder
@AllArgsConstructor
@Entity
@Table(name = "orders")
@SQLDelete(sql = "UPDATE orders SET deleted_at = NOW() WHERE id = ?")
@SQLRestriction("deleted_at IS NULL")
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

    public static Order fromAuction(Long auctionId, Long cardId, Long sellerId, Long buyerId, Long finalPrice) {
        return Order.builder()
                .auctionId(auctionId)
                .cardId(cardId)
                .sellerId(sellerId)
                .buyerId(buyerId)
                .orderUid(TsidGenerator.generateOrderUid())
                .finalPrice(finalPrice)
                .status(OrderStatus.PAYMENT_PENDING)
                .deliveryStatus(DeliveryStatus.PREPARING)
                .build();
    }

    // 빌링키 자동결제(PAYMENT_PENDING) 또는 PG 직접결제(PAYMENT_FAILED) 성공 시 호출
    public void completePayment() {
        if (this.status != OrderStatus.PAYMENT_PENDING && this.status != OrderStatus.PAYMENT_FAILED) {
            throw new OrderException(ErrorCode.ORDER_CANNOT_COMPLETE_PAYMENT);
        }
        this.status = OrderStatus.PAYMENT_COMPLETED;
    }

    // 빌링키 자동결제 실패(PAYMENT_PENDING) 또는 PG 직접결제 실패(PAYMENT_FAILED, 멱등) 시 호출
    public void failPayment() {
        if (this.status != OrderStatus.PAYMENT_PENDING && this.status != OrderStatus.PAYMENT_FAILED) {
            throw new OrderException(ErrorCode.ORDER_CANNOT_FAIL_PAYMENT);
        }
        this.status = OrderStatus.PAYMENT_FAILED;
    }

    public void refund() {
        this.status = OrderStatus.REFUNDED;
        if (this.deliveryStatus != null) {
            this.deliveryStatus = DeliveryStatus.CANCELLED;
        }
    }
}
