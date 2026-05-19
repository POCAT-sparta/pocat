package com.rocketcrew.pocat.domain.payment.service;

import com.rocketcrew.pocat.domain.order.entity.Order;
import com.rocketcrew.pocat.domain.order.repository.OrderRepository;
import com.rocketcrew.pocat.domain.payment.entity.Payment;
import com.rocketcrew.pocat.domain.payment.repository.PaymentRepository;
import com.rocketcrew.pocat.global.exception.common.ErrorCode;
import com.rocketcrew.pocat.global.exception.domain.OrderException;
import com.rocketcrew.pocat.global.exception.domain.PaymentException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 결제 실패 상태를 독립 트랜잭션으로 저장하는 서비스.
 *
 * payment.fail() / order.failPayment() 후 PaymentException을 던지면
 * 외부 @Transactional이 RuntimeException으로 롤백하여 실패 상태가 저장되지 않는다.
 * REQUIRES_NEW로 먼저 커밋하면 외부 트랜잭션 롤백과 무관하게 실패 상태가 유지된다.
 */
@Service
@RequiredArgsConstructor
public class PaymentFailureService {

    private final PaymentRepository paymentRepository;
    private final OrderRepository orderRepository;

    /**
     * orderId를 외부에서 받지 않고 payment.getOrderId()로 파생한다.
     * 두 ID를 독립 파라미터로 받으면 호출부 실수로 무관한 주문 상태가 오염될 수 있다.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markFailed(Long paymentId) {
        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new PaymentException(ErrorCode.PAYMENT_NOT_FOUND));
        Order order = orderRepository.findById(payment.getOrderId())
                .orElseThrow(() -> new OrderException(ErrorCode.ORDER_NOT_FOUND));

        payment.fail();
        order.failPayment();
    }
}
