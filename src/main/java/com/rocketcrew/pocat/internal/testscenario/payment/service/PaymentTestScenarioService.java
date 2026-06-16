package com.rocketcrew.pocat.internal.testscenario.payment.service;

import com.rocketcrew.pocat.domain.order.entity.Order;
import com.rocketcrew.pocat.domain.order.enums.OrderStatus;
import com.rocketcrew.pocat.domain.order.enums.OrderType;
import com.rocketcrew.pocat.domain.order.service.OrderQueryService;
import com.rocketcrew.pocat.domain.payment.entity.Payment;
import com.rocketcrew.pocat.domain.payment.entity.PaymentStatus;
import com.rocketcrew.pocat.domain.payment.entity.PaymentType;
import com.rocketcrew.pocat.domain.payment.repository.PaymentRepository;
import com.rocketcrew.pocat.domain.payment.service.FailureService;
import com.rocketcrew.pocat.domain.payment.service.PaymentCommandService;
import com.rocketcrew.pocat.global.exception.common.ErrorCode;
import com.rocketcrew.pocat.global.exception.domain.OrderException;
import com.rocketcrew.pocat.global.exception.domain.PaymentException;
import com.rocketcrew.pocat.internal.testscenario.payment.dto.AutoPaymentFailureInjectionResponse;
import com.rocketcrew.pocat.internal.testscenario.support.TestScenarioGuard;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentTestScenarioService {

    private final OrderQueryService orderQueryService;
    private final PaymentCommandService paymentCommandService;
    private final FailureService failureService;
    private final PaymentRepository paymentRepository;
    private final TestScenarioGuard testScenarioGuard;

    public AutoPaymentFailureInjectionResponse injectAutoPaymentFailure(String orderUid) {
        testScenarioGuard.ensureEnabled();

        Order beforeOrder = orderQueryService.findByOrderUid(orderUid);
        if (beforeOrder.getStatus() != OrderStatus.PAYMENT_PENDING) {
            throw new OrderException(ErrorCode.ORDER_CANNOT_FAIL_PAYMENT);
        }

        PaymentCommandService.BillingKeyPayment billingKeyPayment =
                paymentCommandService.createBillingKeyPaymentIfAbsent(beforeOrder.getId());
        Payment beforePayment = billingKeyPayment.payment();
        PaymentStatus beforePaymentStatus = beforePayment.getStatus();

        // Keep the production failure path and its transaction boundaries intact.
        failureService.handleAutoPaymentFailure(beforePayment.getId(), beforeOrder.getId());

        Order afterOrder = orderQueryService.findByOrderUid(orderUid);
        Payment afterPayment = paymentRepository.findByOrderIdAndPaymentType(afterOrder.getId(), PaymentType.BILLING_KEY)
                .orElseThrow(() -> new PaymentException(ErrorCode.PAYMENT_NOT_FOUND));

        boolean autoFailureEventPublished = afterOrder.getOrderType() != OrderType.BUYOUT;
        boolean directPaymentAvailable = afterOrder.getStatus() == OrderStatus.AUTO_PAYMENT_FAILED;

        log.info("[TEST_SCENARIO] 자동결제 실패 주입 orderUid={} beforeStatus={} afterStatus={} paymentUid={}",
                orderUid, beforeOrder.getStatus(), afterOrder.getStatus(), afterPayment.getPaymentUid());

        return new AutoPaymentFailureInjectionResponse(
                afterOrder.getOrderUid(),
                afterOrder.getId(),
                afterOrder.getOrderType(),
                beforeOrder.getStatus(),
                afterOrder.getStatus(),
                afterPayment.getPaymentUid(),
                afterPayment.getPaymentType(),
                beforePaymentStatus,
                afterPayment.getStatus(),
                billingKeyPayment.created(),
                autoFailureEventPublished,
                directPaymentAvailable
        );
    }
}
