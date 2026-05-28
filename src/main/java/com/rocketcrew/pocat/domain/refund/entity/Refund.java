package com.rocketcrew.pocat.domain.refund.entity;

import com.rocketcrew.pocat.global.entity.BaseEntity;
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
@Table(name = "refunds")
@SQLDelete(sql = "UPDATE refunds SET deleted_at = NOW() WHERE id = ?")
@SQLRestriction("deleted_at IS NULL")
public class Refund extends BaseEntity {

    @Column(name = "order_id", nullable = false)
    private Long orderId;

    @Column(name = "payment_id", nullable = false)
    private Long paymentId;

    @Column(name = "amount", nullable = false)
    private Long amount;

    @Column(name = "reason", nullable = false, length = 100)
    private String reason;

    @Column(name = "reject_reason", length = 100)
    private String rejectReason;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private RefundStatus status;

    @Builder.Default
    @Column(name = "retry_count", nullable = false)
    private int retryCount = 0;

    @Column(name = "next_retry_at")
    private LocalDateTime nextRetryAt;

    @Column(name = "failure_reason", length = 255)
    private String failureReason;

    public void markProcessing() {
        this.status = RefundStatus.PROCESSING;
    }

    public void markCompleted() {
        this.status = RefundStatus.COMPLETED;
    }

    public void markRetryableFailure(String reason, LocalDateTime nextRetryAt) {
        this.status = RefundStatus.FAILED_RETRYABLE;
        this.failureReason = reason;
        this.nextRetryAt = nextRetryAt;
        this.retryCount++;
    }

    public void markFinalFailure(String reason) {
        this.status = RefundStatus.FAILED_FINAL;
        this.failureReason = reason;
    }

    public void reject(String rejectReason) {
        this.status = RefundStatus.REJECTED;
        this.rejectReason = rejectReason;
    }

    public boolean isRetryDue(LocalDateTime now) {
        return nextRetryAt == null || !now.isBefore(nextRetryAt);
    }
}
