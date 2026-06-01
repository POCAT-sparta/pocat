package com.rocketcrew.pocat.global.outbox.scheduler;

import com.rocketcrew.pocat.global.outbox.repository.OutboxRepository;
import com.rocketcrew.pocat.global.outbox.enums.OutboxStatus;
import com.rocketcrew.pocat.global.outbox.entity.OutboxEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
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
    private final OutboxProcessor outboxProcessor;
    private final long ageThresholdSeconds;

    public OutboxRelayScheduler(
            OutboxRepository outboxRepository,
            OutboxProcessor outboxProcessor,
            @Qualifier("kafkaTemplate") KafkaTemplate<String, String> generalTemplate,
            @Qualifier("paymentKafkaTemplate") KafkaTemplate<String, String> financialTemplate,
            @Value("${outbox.relay.age-threshold-seconds:10}") long ageThresholdSeconds) {
        this.outboxRepository = outboxRepository;
        this.outboxProcessor = outboxProcessor;
        this.generalTemplate = generalTemplate;
        this.financialTemplate = financialTemplate;
        this.ageThresholdSeconds = ageThresholdSeconds;
    }

    @Scheduled(fixedDelay = 5000)
    public void relay() {
        List<OutboxEvent> pendingEvents = outboxRepository
                .findTop100ByStatusAndCreatedAtBeforeOrderByCreatedAtAsc(
                        OutboxStatus.PENDING,
                        LocalDateTime.now().minusSeconds(ageThresholdSeconds)
                );

        for (OutboxEvent event : pendingEvents) {
            if (Thread.currentThread().isInterrupted()) {
                break;
            }
            // 분리된 프로세서에 처리를 위임 (각각 독립된 트랜잭션)
            outboxProcessor.processEvent(event, resolveTemplate(event.getTopic()));
        }
    }

    private KafkaTemplate<String, String> resolveTemplate(String topic) {
        return FINANCIAL_TOPICS.contains(topic) ? financialTemplate : generalTemplate;
    }
}