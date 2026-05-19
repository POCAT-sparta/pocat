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
        if (paymentMethod == null || paymentMethod.isBlank()) {
            throw new IllegalArgumentException("결제 수단은 필수입니다.");
        }
        if (paidAt == null) {
            throw new IllegalArgumentException("결제 완료 시각은 필수입니다.");
        }
        if (this.status != PaymentStatus.PENDING) {
            throw new IllegalStateException(
                    "결제 완료는 PENDING 상태에서만 가능합니다. 현재 상태: " + this.status);
        }
        this.status = PaymentStatus.COMPLETED;
        this.paymentMethod = paymentMethod;
        this.paidAt = paidAt;
    }

    // FAILED → FAILED 멱등 전이 허용: Webhook 재전송·이벤트 리플레이 시 불필요한 예외 방지
    public void fail() {
        if (this.status != PaymentStatus.PENDING && this.status != PaymentStatus.FAILED) {
            throw new IllegalStateException(
                    "결제 실패는 PENDING 또는 FAILED 상태에서만 가능합니다. 현재 상태: " + this.status);
        }
        this.status = PaymentStatus.FAILED;
    }

    public void refund() {
        if (this.status != PaymentStatus.COMPLETED) {
            throw new IllegalStateException(
                    "환불은 COMPLETED 상태에서만 가능합니다. 현재 상태: " + this.status);
        }
        this.status = PaymentStatus.REFUNDED;
    }
}
