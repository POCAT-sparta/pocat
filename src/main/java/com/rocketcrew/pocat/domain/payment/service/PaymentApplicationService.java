package com.rocketcrew.pocat.domain.payment.service;

import com.rocketcrew.pocat.domain.order.entity.Order;
import com.rocketcrew.pocat.domain.order.enums.OrderStatus;
import com.rocketcrew.pocat.domain.order.service.OrderQueryService;
import com.rocketcrew.pocat.domain.payment.client.out.portone.PortOneCancelStatus;
import com.rocketcrew.pocat.domain.payment.client.out.portone.PortOneClientService;
import com.rocketcrew.pocat.domain.payment.client.out.portone.PortOneStatus;
import com.rocketcrew.pocat.domain.payment.client.out.portone.dto.PortOneCancelResponse;
import com.rocketcrew.pocat.domain.payment.client.out.portone.dto.PortOnePaymentResponse;
import com.rocketcrew.pocat.domain.payment.dto.request.CreatePaymentRequest;
import com.rocketcrew.pocat.domain.payment.dto.response.PaymentResponse;
import com.rocketcrew.pocat.domain.payment.entity.Payment;
import com.rocketcrew.pocat.domain.payment.entity.PaymentType;
import com.rocketcrew.pocat.domain.payment.enums.PaymentErrorReason;
import com.rocketcrew.pocat.domain.user.entity.User;
import com.rocketcrew.pocat.domain.user.service.UserQueryService;
import com.rocketcrew.pocat.global.exception.common.ErrorCode;
import com.rocketcrew.pocat.global.exception.domain.PaymentException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentApplicationService {

    private final PortOneClientService portOneClientService;
    private final FailureService failureService;
    private final UserQueryService userQueryService;

    private final PaymentCommandService paymentCommandService;
    private final PaymentQueryService paymentQueryService;
    private final OrderQueryService orderQueryService;

    /**
     * 6.1 결제 요청 — PG 직접결제 레코드 생성
     * 낙찰 후 자동결제(billingKey) 실패 시, 구매자가 PortOne 결제창을 열기 전에
     * 서버가 paymentUid를 먼저 발급하여 금액 위변조를 원천 차단한다.
     */
    @Transactional
    public PaymentResponse generatePayment(Long buyerId, CreatePaymentRequest request) {
        Order order = orderQueryService.findByOrderIdWithLock(request.orderId());

        if (!order.getBuyerId().equals(buyerId)) {
            throw new PaymentException(ErrorCode.PAYMENT_BUYER_MISMATCH);
        }

        // 자동결제 실패 시에만 직접 결제 생성.
        if (order.getStatus() != OrderStatus.AUTO_PAYMENT_FAILED) {
            throw new PaymentException(ErrorCode.PAYMENT_ORDER_NOT_FAILED);
        }

        // 자동결제 실패 시각(updatedAt) 기준 1시간 초과 여부
        if (order.getPaymentDeadline().isBefore(LocalDateTime.now())) {
            throw new PaymentException(ErrorCode.PAYMENT_WINDOW_EXPIRED);
        }

        Payment payment = paymentCommandService.createPayment(order.getId(), PaymentType.PG_DIRECT);
        return PaymentResponse.from(payment);
    }

    public PaymentResponse autoPayment(String orderUid) {
        Order order = orderQueryService.findByOrderUid(orderUid);

        if (order.getStatus() == OrderStatus.PAYMENT_COMPLETED) {
            return paymentQueryService.findByOrderId(order.getId());
        }

        User user = userQueryService.getUserEntity(order.getBuyerId());
        String billingKey = user.getBillingKey();

        if (billingKey == null || billingKey.isEmpty()) {
            throw new PaymentException(ErrorCode.BILLING_KEY_NOT_FOUND);
        }

        Payment payment = paymentCommandService.createPayment(order.getId() , PaymentType.BILLING_KEY);

        PortOnePaymentResponse response = portOneClientService.attemptBillingKeyPayment(
                payment.getPaymentUid(), billingKey, payment.getAmount()
        );

        // 네트워크 에러, 대기, 준비 건은 3번 재시도 하여 데이터 조회
        if(PortOneStatus.NETWORK_ERROR.equals(response.status())
                || PortOneStatus.READY.equals(response.status())
                || PortOneStatus.PAY_PENDING.equals(response.status())
        ) {
            response = attemptWithRetry(payment.getPaymentUid());
        }

        // 이후 성공이 아니면 실패처리
        if (!PortOneStatus.PAID.equals(response.status())) {
            paymentCommandService.handelFailed(payment.getPaymentUid(), order.getId());
            throw new PaymentException(ErrorCode.PAYMENT_STATUS_NOT_PAID);
        }

        // 금액이 맞지 않으면 취소
        if (response.amount() == null || !payment.getAmount().equals(response.amount())) {
            attemptCancelPayment(payment.getPaymentUid(),order.getId() ,response.amount());
            failureService.autoPaymentFailEvent(order.getOrderUid(),order.getBuyerId(),order.getSellerId());
            throw new PaymentException(ErrorCode.PAYMENT_AMOUNT_MISMATCH);
        }

        paymentCommandService.completePayment(payment, order, response.paymentMethod(), response.paidAt());
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

        PortOnePaymentResponse portOneClientPayment = portOneClientService.getPayment(paymentUid);

        payment = paymentQueryService.findPaymentByUidWithLock(paymentUid);

        if (payment.isFinalized()) return PaymentResponse.from(payment);

        if(PortOneStatus.NETWORK_ERROR.equals(portOneClientPayment.status())
                || PortOneStatus.READY.equals(portOneClientPayment.status())
                || PortOneStatus.PAY_PENDING.equals(portOneClientPayment.status())
        ) {
            portOneClientPayment = attemptWithRetry(payment.getPaymentUid());
        }

        if (!PortOneStatus.PAID.equals(portOneClientPayment.status())) {
            failureService.markFailed(paymentUid,order.getId(), PaymentErrorReason.WEBHOOK_FAILED);
            throw new PaymentException(ErrorCode.PAYMENT_STATUS_NOT_PAID);
        }

        if (portOneClientPayment.amount() == null || !payment.getAmount().equals(portOneClientPayment.amount())) {
            attemptCancelPayment(payment.getPaymentUid(),order.getId() ,portOneClientPayment.amount());
            failureService.directPaymentFailEvent(order.getOrderUid(),order.getBuyerId(),order.getSellerId());
            throw new PaymentException(ErrorCode.PAYMENT_AMOUNT_MISMATCH);
        }
        paymentCommandService.completePayment(payment, order, portOneClientPayment.paymentMethod(), portOneClientPayment.paidAt());
        return PaymentResponse.from(payment);
    }

    // resaon 관리는 일단 string 추후 많아지면 enum등으로 관리 필요
    private void attemptCancelPayment(String paymentUid, Long orderId,Long amount) {
        paymentCommandService.handelCancel(paymentUid, orderId);
        PortOneCancelResponse response = portOneClientService.cancelPayment(paymentUid, amount ,"결제금액 불일치");

        if(response.status().equals(PortOneCancelStatus.HTTP_ERROR) || response.status().equals(PortOneCancelStatus.NETWORK_ERROR)) {
            paymentCommandService.cancelFailPayment(paymentUid);
        }
    }


    private PortOnePaymentResponse attemptWithRetry(String paymentUid) {
        int MAX_RETRY = 3;
        for (int attempt = 1; attempt <= MAX_RETRY; attempt++) {
            PortOnePaymentResponse response = portOneClientService.getPayment(paymentUid);

            // 성공, 실패 확정 시 반환
            if (PortOneStatus.PAID.equals(response.status())
                    || PortOneStatus.FAILED.equals(response.status())
                    || PortOneStatus.CANCELLED.equals(response.status())
                    || PortOneStatus.PARTIAL_CANCELLED.equals(response.status())
            ) {
                return response;
            }
            log.warn("결제 미확정 상태 paymentUid={} status={} attempt={}/{}",
                    paymentUid, response.status(), attempt, MAX_RETRY);

            if (attempt < MAX_RETRY) sleepSeconds(attempt);
        }

        throw new PaymentException(ErrorCode.PORTONE_NETWORK_ERROR);
    }

    private void sleepSeconds(int seconds) {
        try {
            Thread.sleep(1000L * seconds);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            throw new PaymentException(ErrorCode.PORTONE_NOT_INTEGRATED, ie);
        }
    }



}
