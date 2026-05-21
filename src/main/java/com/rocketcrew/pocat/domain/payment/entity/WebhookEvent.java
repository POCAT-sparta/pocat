package com.rocketcrew.pocat.domain.payment.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

@Getter
@Entity
@Table(
        name = "webhook_events",
        uniqueConstraints = @UniqueConstraint(columnNames = {"payment_id", "event_type"})
)
@EntityListeners(AuditingEntityListener.class)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class WebhookEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "payment_id", nullable = false, length = 100)
    private String paymentId;

    @Column(name = "event_type", nullable = false, length = 50)
    private String eventType;

    @Column(name = "raw_body", nullable = false, columnDefinition = "TEXT")
    private String rawBody;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private WebhookEventStatus status;

    @CreatedDate
    @Column(updatable = false)
    private LocalDateTime createdAt;

    @Builder
    public WebhookEvent(String paymentId, String eventType, String rawBody, WebhookEventStatus status) {
        this.paymentId = paymentId;
        this.eventType = eventType;
        this.rawBody = rawBody;
        this.status = status;
    }

    public void markProcessed() {
        this.status = WebhookEventStatus.PROCESSED;
    }

    public void markFailed() {
        this.status = WebhookEventStatus.FAILED;
    }
}
