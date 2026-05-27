package com.rocketcrew.pocat.global.event.outbox;

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

    private static final int MAX_RETRY = 5;
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
        List<OutboxEvent> pendingEvents = outboxRepository.findTop100ByStatusOrderByCreatedAtAsc(OutboxStatus.PENDING);

        for (OutboxEvent event : pendingEvents) {
            try {
                resolveTemplate(event.getTopic())
                        .send(event.getTopic(), event.getPartitionKey(), event.getPayload())
                        .get(5, TimeUnit.SECONDS);
                event.markSent();
                log.info("아웃박스 릴레이 발행 성공: id={}, topic={}, eventType={}", event.getId(), event.getTopic(), event.getEventType());
            } catch (Exception e) {
                event.incrementRetry(MAX_RETRY);
                log.error("아웃박스 릴레이 발행 실패: id={}, topic={}, eventType={}, retryCount={}",
                        event.getId(), event.getTopic(), event.getEventType(), event.getRetryCount(), e);
            }
            outboxRepository.save(event);
        }
    }

    private KafkaTemplate<String, String> resolveTemplate(String topic) {
        return FINANCIAL_TOPICS.contains(topic) ? financialTemplate : generalTemplate;
    }
}
