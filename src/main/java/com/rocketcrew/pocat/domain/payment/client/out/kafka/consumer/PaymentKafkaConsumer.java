package com.rocketcrew.pocat.domain.payment.client.out.kafka.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rocketcrew.pocat.domain.order.consumer.orderEvent.OrderEvent;
import com.rocketcrew.pocat.domain.payment.service.PaymentApplicationService;
import com.rocketcrew.pocat.global.exception.common.ServiceException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentKafkaConsumer {

    private final PaymentApplicationService paymentApplicationService;
    private final ObjectMapper objectMapper;

    @KafkaListener(
            topics = "order",
            groupId = "payment-billing-group",
            containerFactory = "paymentKafkaListenerContainerFactory"
    )
    public void consume(String message, Acknowledgment acknowledgment) {
        try {
            OrderEvent event = objectMapper.readValue(message, OrderEvent.class);
            if ("order.created".equals(event.getEventType())) {
                paymentApplicationService.autoPayment(event.getOrderUid());
            }
            acknowledgment.acknowledge();
        } catch (ServiceException e) {
            // 비즈니스 실패는 이미 내부에서 처리 완료 (AUTO_PAYMENT_FAILED + 알림)
            log.warn("payment 자동결제 처리 종료 - skip: {}", message, e);
            acknowledgment.acknowledge();
        } catch (Exception e) {
            // 아예 처리가 안된 상황 오프셋 커밋 X
            log.error("payment 이벤트 처리 중 예상치 못한 예외: {}", message, e);
            throw new RuntimeException(e);
        }
    }
}
