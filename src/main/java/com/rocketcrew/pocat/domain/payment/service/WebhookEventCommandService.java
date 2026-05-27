package com.rocketcrew.pocat.domain.payment.service;

import com.rocketcrew.pocat.domain.payment.entity.WebhookEvent;
import com.rocketcrew.pocat.domain.payment.entity.WebhookEventStatus;
import com.rocketcrew.pocat.domain.payment.repository.WebhookEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * WebhookEvent 상태 전이 전용 서비스.
 *
 * 모든 메서드가 REQUIRES_NEW 트랜잭션을 사용한다.
 * 이유: 웹훅 처리 중 메인 트랜잭션이 롤백되더라도 이벤트 수신 기록과
 * 처리 결과(PROCESSED/FAILED)는 DB에 남아야 감사 추적과 중복 방지가 보장된다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WebhookEventCommandService {

    private final WebhookEventRepository webhookEventRepository;

    /**
     * 웹훅 이벤트를 RECEIVED 상태로 최초 기록한다.
     * (payment_id + event_type) unique 충돌 시 기존 이벤트를 반환한다.
     *
     * @return 새로 저장된 이벤트, 또는 이미 존재하는 이벤트 (중복 웹훅 감지용)
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public WebhookEvent saveReceivedOrGet(String paymentId, String eventType, String rawBody) {
        try {
            WebhookEvent event = WebhookEvent.builder()
                    .paymentId(paymentId)
                    .eventType(eventType)
                    .rawBody(rawBody)
                    .status(WebhookEventStatus.RECEIVED)
                    .build();
            return webhookEventRepository.saveAndFlush(event);
        } catch (DataIntegrityViolationException e) {
            // unique 충돌: 동일 (paymentId, eventType) 이벤트가 이미 존재 → 기존 이벤트 반환
            log.warn("WebhookEvent unique 충돌 — 기존 이벤트 재사용 paymentId={} eventType={}", paymentId, eventType);
            return webhookEventRepository
                    .findByPaymentIdAndEventType(paymentId, eventType)
                    .orElseThrow(() -> new IllegalStateException(
                            "WebhookEvent unique 충돌 후 조회 실패 paymentId=" + paymentId + " eventType=" + eventType));
        }
    }

    /**
     * 이벤트를 PROCESSED 상태로 전이한다.
     * 메인 트랜잭션 성공/실패와 무관하게 PROCESSED 기록을 보존한다.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markProcessed(Long webhookEventId) {
        webhookEventRepository.findById(webhookEventId)
                .ifPresent(WebhookEvent::markProcessed);
    }

    /**
     * 이벤트를 FAILED 상태로 전이한다.
     * 금액 불일치·amount null 등 비즈니스 오류로 결제 실패 처리된 경우에 사용한다.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markFailed(Long webhookEventId) {
        webhookEventRepository.findById(webhookEventId)
                .ifPresent(WebhookEvent::markFailed);
    }
}
