package com.rocketcrew.pocat.global.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rocketcrew.pocat.global.outbox.entity.OutboxEvent;
import com.rocketcrew.pocat.global.outbox.repository.OutboxRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;

@Slf4j
@RequiredArgsConstructor
public abstract class BaseEventProducer {

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;
    private final OutboxRepository outboxRepository;

    protected void send(String topic, String key, BaseEvent event) {

        // 1. 직렬화 먼저
        String payload;
        try {
            payload = objectMapper.writeValueAsString(event);
        } catch (Exception e) {
            log.error("직렬화 실패: topic={}, eventType={}",
                    topic, event.getEventType(), e);
            return; // 직렬화 실패 → 저장할 데이터 없음 → 종료
        }

        // 2. kafkaTemplate.send() 이전에 PENDING 먼저 저장
        OutboxEvent outboxEvent = saveToOutbox(
                OutboxEvent.pending(topic, key, event.getEventType(), payload));

        if (outboxEvent == null) {
            return; // outbox 저장 실패 → 발행 중단
        }

        // 3. Kafka 발행
        kafkaTemplate.send(topic, key, payload)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("Kafka 발행 실패 → PENDING 유지: topic={}, eventType={}",
                                topic, event.getEventType(), ex);
                        // PENDING 유지 → Relay가 재발행 ✅
                    } else {
                        log.info("Kafka 발행 성공: topic={}, eventType={}",
                                topic, event.getEventType());
                        // SENT로 업데이트
                        outboxEvent.markSent();
                        saveToOutbox(outboxEvent);
                    }
                });
    }

    private OutboxEvent saveToOutbox(OutboxEvent outboxEvent) {
        try {
            return outboxRepository.save(outboxEvent);
        } catch (Exception e) {
            log.error("outbox 저장 실패: topic={}, eventType={}",
                    outboxEvent.getTopic(), outboxEvent.getEventType(), e);
            return null;
        }
    }
}