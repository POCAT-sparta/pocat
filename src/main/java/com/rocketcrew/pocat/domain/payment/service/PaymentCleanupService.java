package com.rocketcrew.pocat.domain.payment.service;

import com.rocketcrew.pocat.domain.order.repository.OrderRepository;
import com.rocketcrew.pocat.domain.payment.entity.PaymentStatus;
import com.rocketcrew.pocat.domain.payment.repository.PaymentRepository;
import com.rocketcrew.pocat.domain.payment.entity.Payment;
import com.rocketcrew.pocat.global.exception.domain.OrderException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * PENDING 결제 정리를 건별 독립 트랜잭션으로 처리한다.
 * 스케줄러에서 직접 @Transactional을 쓰면 락 없이 여러 엔티티를 한 번에 플러시해
 * confirmPayment와 상태 불일치가 발생할 수 있으므로 별도 서비스로 분리했다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentCleanupService {

    private final PaymentRepository paymentRepository;
    private final OrderRepository orderRepository;

    @Transactional
    public void cleanupOne(String paymentUid) {
        // 비관적 락 획득 — confirmPayment / webhook과의 동시 처리 직렬화
        Payment payment = paymentRepository.findByPaymentUidWithLock(paymentUid).orElse(null);
        if (payment == null || payment.getStatus() != PaymentStatus.PENDING) {
            return; // 이미 다른 경로에서 처리됨
        }

        payment.fail();

        orderRepository.findById(payment.getOrderId()).ifPresent(order -> {
            try {
                order.failPayment();
            } catch (OrderException e) {
                // 주문이 이미 다른 상태로 전이된 경우(PAYMENT_COMPLETED 등) — 정상 케이스
                log.debug("주문 상태 전이 스킵 orderId={} status={}", order.getId(), order.getStatus());
            }
        });

        log.info("만료된 PENDING 결제 정리 완료 paymentUid={} type={}", paymentUid, payment.getPaymentType());
    }
}
