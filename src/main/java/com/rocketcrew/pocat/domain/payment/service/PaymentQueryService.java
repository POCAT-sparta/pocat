package com.rocketcrew.pocat.domain.payment.service;

import com.rocketcrew.pocat.domain.order.entity.Order;
import com.rocketcrew.pocat.domain.order.repository.OrderRepository;
import com.rocketcrew.pocat.domain.payment.dto.response.PaymentResponse;
import com.rocketcrew.pocat.domain.payment.entity.Payment;
import com.rocketcrew.pocat.domain.payment.repository.PaymentRepository;
import com.rocketcrew.pocat.global.exception.common.ErrorCode;
import com.rocketcrew.pocat.global.exception.domain.OrderException;
import com.rocketcrew.pocat.global.exception.domain.PaymentException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PaymentQueryService {

    private final PaymentRepository paymentRepository;
    private final OrderRepository orderRepository;

    /**
     * 6.3 결제 상세 조회
     * 본인(buyer) 또는 ADMIN만 접근 가능.
     */
    public PaymentResponse getPayment(Long requesterId, String paymentUid) {
        Payment payment = findPaymentByUid(paymentUid);
        Order order = findOrder(payment.getOrderId());

        if (!isAdmin() && !order.getBuyerId().equals(requesterId)) {
            throw new PaymentException(ErrorCode.PAYMENT_BUYER_MISMATCH);
        }

        return PaymentResponse.from(payment);
    }

    // ── 내부 헬퍼 ────────────────────────────────────────────────────

    private Payment findPaymentByUid(String paymentUid) {
        return paymentRepository.findByPaymentUid(paymentUid)
                .orElseThrow(() -> new PaymentException(ErrorCode.PAYMENT_NOT_FOUND));
    }

    private Order findOrder(Long orderId) {
        return orderRepository.findById(orderId)
                .orElseThrow(() -> new OrderException(ErrorCode.ORDER_NOT_FOUND));
    }

    private boolean isAdmin() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null && auth.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
    }
}
