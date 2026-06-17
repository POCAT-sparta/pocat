package com.rocketcrew.pocat.domain.payment.service;

import com.rocketcrew.pocat.domain.payment.dto.response.PaymentResponse;
import com.rocketcrew.pocat.domain.payment.entity.Payment;
import com.rocketcrew.pocat.domain.payment.enums.PaymentErrorReason;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class PaymentTxService {

    private final PaymentCommandService paymentCommandService;
    private final FailureService failureService;

    public PaymentResponse completePayment(Long paymentId, Long orderId, String paymentMethod, LocalDateTime paidAt) {
        Payment completedPayment = paymentCommandService.completePayment(paymentId, orderId, paymentMethod, paidAt);
        return PaymentResponse.from(completedPayment);
    }

    public void handleAutoPaymentFailure(Long paymentId, Long orderId) {
        failureService.handleAutoPaymentFailure(paymentId, orderId);
    }

    public void markFailed(String paymentUid, Long orderId, PaymentErrorReason reason) {
        failureService.markFailed(paymentUid, orderId, reason);
    }

    public void handleCancel(String paymentUid, Long orderId) {
        paymentCommandService.handleCancel(paymentUid, orderId);
    }

    public void cancelFailPayment(String paymentUid) {
        paymentCommandService.cancelFailPayment(paymentUid);
    }

    public void directPaymentFailEvent(String orderUid, Long buyerId, Long sellerId) {
        failureService.directPaymentFailEvent(orderUid, buyerId, sellerId);
    }

    public void autoPaymentFailEvent(String orderUid, Long buyerId, Long sellerId) {
        failureService.autoPaymentFailEvent(orderUid, buyerId, sellerId);
    }
}
