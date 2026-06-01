package com.rocketcrew.pocat.domain.settlement.consumer.paymentEvent;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rocketcrew.pocat.domain.settlement.service.SettlementCommandService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class SettlePaymentEventConsumer {

    private final SettlementCommandService settlementCommandService;
    private final ObjectMapper objectMapper;

    @KafkaListener(
            topics = "payment",
            groupId = "settlement-payment-group",
            containerFactory = "paymentKafkaListenerContainerFactory")
    public void consume(String message, Acknowledgment acknowledgment) {
        try {
            PaymentEvent event = objectMapper.readValue(message, PaymentEvent.class);
            if ("payment.completed".equals(event.getEventType())) {
                settlementCommandService.createSettlement(event.getOrderUid());
            }
            acknowledgment.acknowledge();
        } catch (Exception e) {
            log.error("정산 생성 실패 - payment 이벤트 처리 오류: {}", message, e);
            throw new RuntimeException(e);
        }
    }
}
