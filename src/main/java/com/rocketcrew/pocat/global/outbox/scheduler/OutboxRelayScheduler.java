package com.rocketcrew.pocat.global.outbox.scheduler;

import com.rocketcrew.pocat.global.outbox.repository.OutboxRepository;
import com.rocketcrew.pocat.global.outbox.enums.OutboxStatus;
import com.rocketcrew.pocat.global.outbox.entity.OutboxEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
public class OutboxRelayScheduler {

    private static final Set<String> FINANCIAL_TOPICS = Set.of("payment", "refund", "settlement");

    private final OutboxRepository outboxRepository;
    private final KafkaTemplate<String, String> generalTemplate;
    private final KafkaTemplate<String, String> financialTemplate;

    public OutboxRelayScheduler(
            OutboxRepository outboxRepository,
            @Qualifier("kafkaTemplate") KafkaTemplate<String, String> generalTemplate,
            @Qualifier("paymentKafkaTemplate") KafkaTemplate<String, String> financialTemplate) {
        this.outboxRepository = outboxRepository;
        this.generalTemplate = generalTemplate;
        this.financialTemplate = financialTemplate;
    }

    @Scheduled(fixedDelay = 30_000)
    public void relay() {
        List<OutboxEvent> pendingEvents = outboxRepository
                .findTop100ByStatusOrderByCreatedAtAsc(OutboxStatus.PENDING);

        for (OutboxEvent event : pendingEvents) {
            publishIfClaimed(event);
        }
    }

    private void publishIfClaimed(OutboxEvent event) {

        // 1. 조건부 UPDATE로 선점 (다중 서버 중복 발행 방지)
        int updated = outboxRepository.markProcessingIfPending(
                event.getId(),
                OutboxStatus.PENDING,
                OutboxStatus.PROCESSING
        );

        if (updated == 0) {
            // 다른 서버가 먼저 선점 → 스킵
            log.debug("이미 다른 서버가 선점: id={}", event.getId());
            return;
        }

        try {
            // 2. Kafka 발행 (동기 대기)
            resolveTemplate(event.getTopic())
                    .send(event.getTopic(), event.getPartitionKey(), event.getPayload())
                    .get(5, TimeUnit.SECONDS);

            // 3. 발행 성공 → SENT
            event.markSent();
            log.info("릴레이 성공: id={}, topic={}, eventType={}",
                    event.getId(), event.getTopic(), event.getEventType());

        } catch (InterruptedException e) {
            // 4. 인터럽트 → 플래그 복원 후 루프 종료
            Thread.currentThread().interrupt();
            log.warn("릴레이 인터럽트: id={}", event.getId());
            event.markPendingForRetry(); // PENDING 복원
            outboxRepository.save(event);
            return;

        } catch (Exception e) {
            // 5. 발행 실패 → 재시도 or FAILED
            event.markPendingForRetry();
            log.error("릴레이 실패: id={}, retryCount={}",
                    event.getId(), event.getRetryCount(), e);
        }

        outboxRepository.save(event);
    }

    private KafkaTemplate<String, String> resolveTemplate(String topic) {
        return FINANCIAL_TOPICS.contains(topic) ? financialTemplate : generalTemplate;
    }
}