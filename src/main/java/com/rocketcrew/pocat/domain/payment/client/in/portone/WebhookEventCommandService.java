package com.rocketcrew.pocat.domain.payment.client.in.portone;

import com.rocketcrew.pocat.domain.payment.entity.WebhookEvent;
import com.rocketcrew.pocat.domain.payment.entity.WebhookEventStatus;
import com.rocketcrew.pocat.domain.payment.repository.WebhookEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
     * 이미 존재하는 이벤트가 있으면 그대로 반환한다 (중복 웹훅 감지용).
     *
     * 설계 노트:
     * try-catch(DataIntegrityViolationException) 패턴을 사용하지 않는다.
     * 이유: saveAndFlush()에서 예외 발생 시 Spring이 REQUIRES_NEW 트랜잭션을
     * rollback-only로 마킹하여, catch 블록 내 조회 호출이 UnexpectedRollbackException을 유발한다.
     *
     * check-first 패턴의 TOCTOU 가능성:
     * 두 스레드가 동시에 find → 미존재 → save를 실행하면 하나는 unique 충돌로 실패한다.
     * 실패한 경우 DataIntegrityViolationException이 전파되어 PortOne이 재전송하며,
     * 재전송 시점에는 기존 레코드가 이미 커밋된 상태이므로 find에서 정상 반환된다.
     * PortOne 재전송 간격(수 분)을 고려하면 실질적 동시 충돌 가능성은 극히 낮다.
     *
     * @return 새로 저장된 이벤트, 또는 이미 존재하는 이벤트
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public WebhookEvent saveReceivedOrGet(String paymentId, String eventType, String rawBody) {
        return webhookEventRepository.findByPaymentIdAndEventType(paymentId, eventType)
                .orElseGet(() -> {
                    log.debug("WebhookEvent 신규 기록 paymentId={} eventType={}", paymentId, eventType);
                    WebhookEvent event = WebhookEvent.builder()
                            .paymentId(paymentId)
                            .eventType(eventType)
                            .rawBody(rawBody)
                            .status(WebhookEventStatus.RECEIVED)
                            .build();
                    return webhookEventRepository.save(event);
                });
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
