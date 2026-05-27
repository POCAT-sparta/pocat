package com.rocketcrew.pocat.domain.payment.service;

import com.rocketcrew.pocat.domain.order.entity.Order;
import com.rocketcrew.pocat.domain.order.enums.OrderStatus;
import com.rocketcrew.pocat.domain.order.service.OrderQueryService;
import com.rocketcrew.pocat.domain.payment.client.PortOneClient;
import com.rocketcrew.pocat.domain.payment.client.PortOnePaymentResponse;
import com.rocketcrew.pocat.domain.payment.dto.request.CreatePaymentRequest;
import com.rocketcrew.pocat.domain.payment.dto.response.PaymentResponse;
import com.rocketcrew.pocat.domain.payment.entity.Payment;
import com.rocketcrew.pocat.domain.payment.entity.PaymentStatus;
import com.rocketcrew.pocat.domain.user.entity.User;
import com.rocketcrew.pocat.domain.user.repository.UserRepository;
import com.rocketcrew.pocat.global.exception.common.ErrorCode;
import com.rocketcrew.pocat.global.exception.domain.PaymentException;
import com.rocketcrew.pocat.global.exception.domain.UserException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentApplicationService {

    private final PortOneClient portOneClient;
    private final FailureService failureService;
    private final UserRepository userRepository;

    private final PaymentCommandService paymentCommandService;
    private final PaymentQueryService paymentQueryService;
    private final OrderQueryService orderQueryService;

    /**
     * 6.1 결제 요청 — PG 직접결제 레코드 생성
     * 낙찰 후 자동결제(billingKey) 실패 시, 구매자가 PortOne 결제창을 열기 전에
     * 서버가 paymentUid를 먼저 발급하여 금액 위변조를 원천 차단한다.
     */
    public PaymentResponse generatePayment(Long buyerId, CreatePaymentRequest request) {
        Order order = orderQueryService.findByOrderid(request.orderId());

        if (!order.getBuyerId().equals(buyerId)) {
            throw new PaymentException(ErrorCode.PAYMENT_BUYER_MISMATCH);
        }

        if (order.getStatus() != OrderStatus.PAYMENT_FAILED) {
            throw new PaymentException(ErrorCode.PAYMENT_ORDER_NOT_FAILED);
        }

        // 자동결제 실패 시각(updatedAt) 기준 1시간 초과 여부
        if (order.getUpdatedAt().plusHours(1).isBefore(LocalDateTime.now())) {
            throw new PaymentException(ErrorCode.PAYMENT_WINDOW_EXPIRED);
        }

        // 이미 PENDING 레코드가 있으면 기존 paymentUid 반환 (중복 방지 / 멱등성)
        Optional<Payment> existing = paymentQueryService.findByOrderIdAndStatus(
                request.orderId(), PaymentStatus.PENDING);
        if (existing.isPresent()) {
            return PaymentResponse.from(existing.get());
        }

        Payment payment = paymentCommandService.createPayment(order);
        return PaymentResponse.from(payment);
    }

    // 즉시구매, 자동 결제 둘다 이거 호출하면 됨.
    public PaymentResponse autoPayment(Long orderId) {
        Order order = orderQueryService.findByOrderid(orderId);

        if (order.getStatus() == OrderStatus.PAYMENT_COMPLETED) {
            return paymentQueryService.findByOrderId(order.getId());
        }

        User user = findUser(order.getBuyerId());
        String billingKey = user.getBillingKey();

        if (billingKey == null || billingKey.isEmpty()) {
            throw new PaymentException(ErrorCode.BILLING_KEY_NOT_FOUND);
        }

        Payment payment = paymentCommandService.createPayment(order);

        PortOnePaymentResponse response = portOneClient.attemptBillingKeyPayment(
                payment.getPaymentUid(), billingKey, payment.getAmount()
        );

        if (!"PAID".equals(response.status())) {
            failureService.handleBillingKeyPaymentFailure(payment, order, orderId);
        }

        paymentCommandService.completePayment(payment, order, response.paymentMethod(), response.paidAt());
        // TODO : 성공 이벤트 발행
        return PaymentResponse.from(payment);
    }

    /**
     * 6.2 결제 확정 요청 — Client Confirm 경로
     * 클라이언트가 PortOne SDK 결제 완료 후 서버에 확정을 요청.
     * 서버는 PortOne API를 직접 재조회해 금액·상태를 검증한 뒤 DB를 업데이트한다.
     * Webhook과 멱등성을 공유한다 (먼저 도착한 쪽이 처리, 나머지는 스킵).
     */
    public PaymentResponse confirmPayment(Long buyerId, String paymentUid) {
        Payment payment = paymentQueryService.findPaymentByUid(paymentUid);
        Order order = orderQueryService.findByOrderid(payment.getOrderId());

        if (!order.getBuyerId().equals(buyerId)) {
            throw new PaymentException(ErrorCode.PAYMENT_BUYER_MISMATCH);
        }

        if (payment.isFinalized()) return PaymentResponse.from(payment);

        PortOnePaymentResponse portOneClientPayment = portOneClient.getPayment(paymentUid);

        payment = paymentQueryService.findPaymentByUidWithLock(paymentUid);

        if (payment.isFinalized()) return PaymentResponse.from(payment);

        if (!"PAID".equals(portOneClientPayment.status())) {
            throw new PaymentException(ErrorCode.PAYMENT_STATUS_NOT_PAID);
        }

        if (!payment.getAmount().equals(portOneClientPayment.amount())) {
            throw new PaymentException(ErrorCode.PAYMENT_AMOUNT_MISMATCH);
        }

        paymentCommandService.completePayment(payment, order, portOneClientPayment.paymentMethod(), portOneClientPayment.paidAt());

        return PaymentResponse.from(payment);
    }

    private User findUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new UserException(ErrorCode.USER_NOT_FOUND));
    }

}
