package com.rocketcrew.pocat.global.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rocketcrew.pocat.global.event.outbox.OutboxEvent;
import com.rocketcrew.pocat.global.event.outbox.OutboxRepository;
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
        try {
            String payload = objectMapper.writeValueAsString(event);
            kafkaTemplate.send(topic, key, payload)
                    .whenComplete((result, ex) -> {
                        if (ex != null) {
                            log.error("Kafka 발행 실패, 아웃박스에 저장: topic={}, eventType={}", topic, event.getEventType(), ex);
                            saveToOutbox(OutboxEvent.pending(topic, key, event.getEventType(), payload));
                        } else {
                            log.info("Kafka 발행 성공: topic={}, eventType={}", topic, event.getEventType());
                            saveToOutbox(OutboxEvent.sent(topic, key, event.getEventType(), payload));
                        }
                    });
        } catch (Exception e) {
            log.error("이벤트 직렬화 실패: topic={}, eventType={}", topic, event.getEventType(), e);
        }
    }

    private void saveToOutbox(OutboxEvent outboxEvent) {
        try {
            outboxRepository.save(outboxEvent);
        } catch (Exception e) {
            log.error("아웃박스 저장 실패: topic={}, eventType={}", outboxEvent.getTopic(), outboxEvent.getEventType(), e);
        }
    }
}
