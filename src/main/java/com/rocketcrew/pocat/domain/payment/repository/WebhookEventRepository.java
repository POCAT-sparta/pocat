package com.rocketcrew.pocat.domain.payment.repository;

import com.rocketcrew.pocat.domain.payment.entity.WebhookEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface WebhookEventRepository extends JpaRepository<WebhookEvent, Long> {

    Optional<WebhookEvent> findByPaymentIdAndEventType(String paymentId, String eventType);
}
