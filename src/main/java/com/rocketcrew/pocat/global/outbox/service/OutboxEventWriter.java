package com.rocketcrew.pocat.global.outbox.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rocketcrew.pocat.global.event.BaseEvent;
import com.rocketcrew.pocat.global.outbox.entity.OutboxEvent;
import com.rocketcrew.pocat.global.outbox.repository.OutboxRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class OutboxEventWriter {

    private final OutboxRepository outboxRepository;
    private final ObjectMapper objectMapper;

    /**
     * 비즈니스 이벤트를 JSON 페이로드로 변환하여 아웃박스 테이블에 PENDING 상태로 저장하고,
     * 이벤트 객체에 발급된 outboxId를 자동으로 매핑해 줍니다.
     *
     * @param topic        카프카 토픽명
     * @param partitionKey 카프카 파티션 키 (예: orderUid 등)
     * @param event        발행할 비즈니스 이벤트 객체
     */
    public void write(String topic, String partitionKey, BaseEvent event) {
        try {
            // 1. 자바 이벤트 객체를 JSON 텍스트(Payload)로 변환
            String payload = objectMapper.writeValueAsString(event);

            // 2. 아웃박스 테이블에 PENDING 상태로 저장
            OutboxEvent outboxEvent = outboxRepository.save(
                    OutboxEvent.pending(topic, partitionKey, event.getEventType(), payload)
            );

            // 3. ⭐️ 아주 중요한 작업: 발급된 아웃박스 고유 ID를 이벤트 가방에 쏙 넣어주기!
            event.bindOutboxId(outboxEvent.getId());

        } catch (Exception e) {
            // 직렬화 실패나 DB 저장 실패 시 예외를 던져 메인 비즈니스 로직까지 통째로 롤백시킵니다.
            throw new RuntimeException("아웃박스 이벤트 저장 중 치명적 에러 발생: " + event.getEventType(), e);
        }
    }
}
