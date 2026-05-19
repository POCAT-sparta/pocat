package com.rocketcrew.pocat.domain.payment.entity;

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
@Table(name = "payments")
@SQLDelete(sql = "UPDATE payments SET deleted_at = NOW() WHERE id = ?")
public class Payment extends BaseEntity {

    @Column(name = "order_id", nullable = false)
    private Long orderId;

    @Column(name = "payment_uid", nullable = false, length = 50, unique = true)
    private String paymentUid;

    @Column(name = "amount", nullable = false)
    private Long amount;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_type", nullable = false, length = 20)
    private PaymentType paymentType;

    // PG_DIRECT 결제창에서 사용자가 수단을 선택하므로 초기 생성 시 null, 확정 시 업데이트
    @Column(name = "payment_method", length = 50)
    private String paymentMethod;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private PaymentStatus status;

    @Column(name = "paid_at")
    private LocalDateTime paidAt;

    public void complete(String paymentMethod, LocalDateTime paidAt) {
        this.status = PaymentStatus.COMPLETED;
        this.paymentMethod = paymentMethod;
        this.paidAt = paidAt;
    }

    public void fail() {
        this.status = PaymentStatus.FAILED;
    }

    public void refund() {
        this.status = PaymentStatus.REFUNDED;
    }
}
