package com.rocketcrew.pocat.global.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;

@Slf4j
@RequiredArgsConstructor
public abstract class BaseEventProducer {

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    protected void send(String topic, String key, BaseEvent event) {
        try {
            String message = objectMapper.writeValueAsString(event);
            kafkaTemplate.send(topic, key, message)
                    .whenComplete((result, ex) -> {
                        if (ex != null) {
                            log.error("Kafka 발행 실패: topic={}, eventType={}", topic, event.getEventType(), ex);
                        } else {
                            log.info("Kafka 발행: topic={}, eventType={}", topic, event.getEventType());
                        }
                    });
        } catch (Exception e) {
            log.error("Kafka 발행 실패: topic={}, eventType={}",
                    topic, event.getEventType(), e);
        }
    }
}
