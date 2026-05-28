package com.rocketcrew.pocat.global.outbox.scheduler;

import com.rocketcrew.pocat.global.outbox.entity.OutboxEvent;
import com.rocketcrew.pocat.global.outbox.enums.OutboxStatus;
import com.rocketcrew.pocat.global.outbox.repository.OutboxRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.concurrent.TimeUnit;

@Component
@RequiredArgsConstructor
@Slf4j
public class OutboxProcessor {

    private final OutboxRepository outboxRepository;

    // REQUIRES_NEW를 통해 건당 독립적인 트랜잭션을 보장합니다.
    // 하나의 이벤트가 실패하거나 롤백되어도 다른 이벤트에 영향을 주지 않습니다.
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void processEvent(OutboxEvent event, KafkaTemplate<String, String> template) {

        // 1. 조건부 UPDATE로 선점 (Atomic)
        int updated = outboxRepository.markProcessingIfPending(
                event.getId(), OutboxStatus.PENDING, OutboxStatus.PROCESSING
        );

        if (updated == 0) return; // 이미 다른 서버가 선점

        try {
            // 2. 영속성 컨텍스트의 객체 상태도 동기화 (save 시 덮어쓰기 방지)
            event.changeStatusToProcessing(); // 엔티티에 내부 상태 변경 메서드 추가 권장

            // 3. Kafka 발행 (동기 대기)
            template.send(event.getTopic(), event.getPartitionKey(), event.getPayload())
                    .get(5, TimeUnit.SECONDS);

            // 4. 발행 성공 ➡️ SENT
            event.markSent();
            log.info("릴레이 성공: id={}, topic={}", event.getId(), event.getTopic());

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("릴레이 인터럽트 발생: id={}", event.getId());
            event.markPendingForRetry();
            // 무조건 트랜잭션 내에서 변경 감지(Dirty Checking)로 저장되도록 유도
        } catch (Exception e) {
            event.markPendingForRetry();
            log.error("릴레이 실패: id={}, retryCount={}", event.getId(), event.getRetryCount(), e);
        }

        // 영속 상태이므로 메서드 종료 시(커밋 시점) 자동으로 DB에 UPDATE가 날아갑니다.
    }
}
