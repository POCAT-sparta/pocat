package com.rocketcrew.pocat.global.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rocketcrew.pocat.global.outbox.entity.OutboxEvent;
import com.rocketcrew.pocat.global.outbox.repository.OutboxRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@RequiredArgsConstructor
public abstract class BaseEventProducer {


    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;
    private final OutboxRepository outboxRepository;

    // REQUIRES_NEW를 사용하여 이미 커밋된 메인 트랜잭션과 별개로 아웃박스 상태를 업데이트합니다.
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    protected void send(
            String topic,
            String key,
            Long outboxId,
            BaseEvent event
    ) {
        try {
            String payload = objectMapper.writeValueAsString(event);

            // 1. 카프카 동기 전송
            kafkaTemplate.send(topic, key, payload).get();

            log.info("Kafka 발행 성공: topic={}, eventType={}", topic, event.getEventType()
            );
            // 2. 아웃박스 상태를 SENT로 변경 (성공 시에만)
            outboxRepository.findById(outboxId).ifPresent(outboxEvent -> {
                outboxEvent.markSent(); //
                outboxRepository.save(outboxEvent);
            });
        } catch (Exception e) {
            log.error("Kafka 발행 실패: topic={}, eventType={}", topic, event.getEventType(), e);
            // 예외시 스케줄러가 처리할 수 있도록 예외던지지 않기.
        }
    }
}
