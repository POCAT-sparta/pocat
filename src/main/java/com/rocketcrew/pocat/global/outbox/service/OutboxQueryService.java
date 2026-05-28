package com.rocketcrew.pocat.global.outbox.service;

import com.rocketcrew.pocat.domain.payment.event.PaymentBaseEvent;
import com.rocketcrew.pocat.global.event.BaseEvent;
import com.rocketcrew.pocat.global.outbox.enums.OutboxStatus;
import com.rocketcrew.pocat.global.outbox.repository.OutboxRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class OutboxQueryService {

    private final OutboxRepository outboxRepository;

    public boolean checkIfOutboxExists(String topic,BaseEvent event) {
        String partitionKey;

        if (event instanceof PaymentBaseEvent paymentEvent) {
            partitionKey = paymentEvent.getOrderUid();
        } else {
            return false;
        }

        return outboxRepository.existsByTopicAndPartitionKeyAndEventTypeAndStatusIn(
                topic,
                partitionKey,
                event.getEventType(),
                List.of(OutboxStatus.PENDING, OutboxStatus.PROCESSING, OutboxStatus.SENT)
        );
    }
}
