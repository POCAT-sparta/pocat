package com.rocketcrew.pocat.domain.order.entity;

import com.rocketcrew.pocat.domain.order.enums.DeliveryStatus;
import com.rocketcrew.pocat.domain.order.enums.OrderStatus;
import com.rocketcrew.pocat.domain.order.enums.OrderType;
import com.rocketcrew.pocat.global.entity.BaseEntity;
import com.rocketcrew.pocat.global.exception.common.ErrorCode;
import com.rocketcrew.pocat.global.exception.domain.OrderException;
import com.rocketcrew.pocat.global.util.TsidGenerator;
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

    @Column(name = "payment_deadline")
    private LocalDateTime paymentDeadline;

    @Column(name = "bidder_rank")
    private Integer bidderRank;

    @Enumerated(EnumType.STRING)
    @Column(name = "order_type", nullable = false, length = 20)
    private OrderType orderType;

    public void startDirectPayment(LocalDateTime deadline) {
        if (this.status != OrderStatus.PAYMENT_PENDING) {
            throw new OrderException(ErrorCode.ORDER_CANNOT_FAIL_PAYMENT);
        }
        this.status = OrderStatus.AUTO_PAYMENT_FAILED;
        this.paymentDeadline = deadline;
    }

    public void cancel(String reason) {
        this.status = OrderStatus.CANCELLED;
        this.cancelReason = reason;
        if (this.deliveryStatus != null) {
            this.deliveryStatus = DeliveryStatus.CANCELLED;
        }
    }

    // 경매구매용
    public static Order fromAuction(Long auctionId, Long cardId, Long sellerId, Long buyerId, Long finalPrice, Integer bidderRank) {
        return Order.builder()
                .auctionId(auctionId)
                .cardId(cardId)
                .sellerId(sellerId)
                .buyerId(buyerId)
                .orderUid(TsidGenerator.generateOrderUid())
                .finalPrice(finalPrice)
                .status(OrderStatus.PAYMENT_PENDING)
                .deliveryStatus(DeliveryStatus.PREPARING)
                .bidderRank(bidderRank)
                .orderType(OrderType.AUCTION)
                .build();
    }

    // 즉시구매용
    public static Order fromBuyout(Long auctionId, Long cardId, Long sellerId, Long buyerId, Long finalPrice) {
        return Order.builder()
                .auctionId(auctionId)
                .cardId(cardId)
                .sellerId(sellerId)
                .buyerId(buyerId)
                .orderUid(TsidGenerator.generateOrderUid())
                .finalPrice(finalPrice)
                .status(OrderStatus.PAYMENT_PENDING)
                .deliveryStatus(DeliveryStatus.PREPARING)
                .orderType(OrderType.BUYOUT)
                .build();
    }

    // 빌링키 자동결제(PAYMENT_PENDING) 또는 PG 직접결제(AUTO/DIRECT_PAYMENT_FAILED) 성공 시 호출
    public void completePayment() {
        if (this.status != OrderStatus.PAYMENT_PENDING
                && this.status != OrderStatus.AUTO_PAYMENT_FAILED
                && this.status != OrderStatus.DIRECT_PAYMENT_FAILED) {
            throw new OrderException(ErrorCode.ORDER_CANNOT_COMPLETE_PAYMENT);
        }
        this.status = OrderStatus.PAYMENT_COMPLETED;
    }

    // 빌링키 자동결제 실패(PAYMENT_PENDING→AUTO) 또는 PG 직접결제 실패(AUTO/DIRECT→DIRECT, 멱등) 시 호출
    public void failPayment() {
        if (this.status != OrderStatus.PAYMENT_PENDING
                && this.status != OrderStatus.AUTO_PAYMENT_FAILED
                && this.status != OrderStatus.DIRECT_PAYMENT_FAILED) {
            throw new OrderException(ErrorCode.ORDER_CANNOT_FAIL_PAYMENT);
        }
        this.status = this.status == OrderStatus.PAYMENT_PENDING
                ? OrderStatus.AUTO_PAYMENT_FAILED
                : OrderStatus.DIRECT_PAYMENT_FAILED;
    }

    public void refund() {
        this.status = OrderStatus.REFUNDED;
        if (this.deliveryStatus != null) {
            this.deliveryStatus = DeliveryStatus.CANCELLED;
        }
    }
}
