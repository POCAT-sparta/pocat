package com.rocketcrew.pocat.domain.payment.service;

import com.rocketcrew.pocat.domain.order.entity.Order;
import com.rocketcrew.pocat.domain.order.enums.OrderStatus;
import com.rocketcrew.pocat.domain.order.repository.OrderRepository;
import com.rocketcrew.pocat.domain.payment.dto.request.CreatePaymentRequest;
import com.rocketcrew.pocat.domain.payment.dto.request.WebhookRequest;
import com.rocketcrew.pocat.domain.payment.dto.response.PaymentResponse;
import com.rocketcrew.pocat.domain.payment.entity.Payment;
import com.rocketcrew.pocat.domain.payment.entity.PaymentStatus;
import com.rocketcrew.pocat.domain.payment.entity.PaymentType;
import com.rocketcrew.pocat.domain.payment.repository.PaymentRepository;
import com.rocketcrew.pocat.global.exception.common.ErrorCode;
import com.rocketcrew.pocat.global.exception.domain.OrderException;
import com.rocketcrew.pocat.global.exception.domain.PaymentException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional
public class PaymentCommandService {

    private final PaymentRepository paymentRepository;
    private final OrderRepository orderRepository;

    /**
     * 6.1 결제 요청 — PG 직접결제 레코드 생성
     * 낙찰 후 자동결제(billingKey) 실패 시, 구매자가 PortOne 결제창을 열기 전에
     * 서버가 paymentUid를 먼저 발급하여 금액 위변조를 원천 차단한다.
     */
    public PaymentResponse createPayment(Long buyerId, CreatePaymentRequest request) {
        Order order = findOrder(request.orderId());

        validateBuyer(order, buyerId);

        if (order.getStatus() != OrderStatus.PAYMENT_FAILED) {
            throw new PaymentException(ErrorCode.PAYMENT_ORDER_NOT_FAILED);
        }

        // 자동결제 실패 시각(updatedAt) 기준 1시간 초과 여부
        if (order.getUpdatedAt().plusHours(1).isBefore(LocalDateTime.now())) {
            throw new PaymentException(ErrorCode.PAYMENT_WINDOW_EXPIRED);
        }

        // 이미 PENDING 레코드가 있으면 기존 paymentUid 반환 (중복 방지 / 멱등성)
        Optional<Payment> existing = paymentRepository.findByOrderIdAndStatus(
                request.orderId(), PaymentStatus.PENDING);
        if (existing.isPresent()) {
            return PaymentResponse.from(existing.get());
        }

        // 금액은 서버가 orders.final_price에서 직접 확정 (클라이언트 금액 신뢰 금지)
        Payment payment = Payment.builder()
                .orderId(order.getId())
                .paymentUid(generatePaymentUid())
                .amount(order.getFinalPrice())
                .paymentType(PaymentType.PG_DIRECT)
                .status(PaymentStatus.PENDING)
                .build();

        return PaymentResponse.from(paymentRepository.save(payment));
    }

    /**
     * 6.2 결제 확정 요청 — Client Confirm 경로
     * 클라이언트가 PortOne SDK 결제 완료 후 서버에 확정을 요청.
     * 서버는 PortOne API를 직접 재조회해 금액·상태를 검증한 뒤 DB를 업데이트한다.
     * Webhook과 멱등성을 공유한다 (먼저 도착한 쪽이 처리, 나머지는 스킵).
     */
    public PaymentResponse confirmPayment(Long buyerId, String paymentUid) {
        Payment payment = findPaymentByUid(paymentUid);
        Order order = findOrder(payment.getOrderId());

        validateBuyer(order, buyerId);

        // 멱등성: 이미 최종 처리된 경우 현재 상태 그대로 반환
        if (isFinalized(payment.getStatus())) {
            return PaymentResponse.from(payment);
        }

        // TODO: PortOne API 호출 → GET https://api.portone.io/v2/payments/{paymentUid}
        //   - 응답 status가 "PAID"인지 확인
        //   - 응답 amount.total이 payment.getAmount()와 일치하는지 검증
        //   → 불일치 시: PortOne 결제 취소 API 호출 후 payment.fail(), order.failPayment()
        // 아래는 PortOne 연동 완료 후 실제 응답 값으로 교체 필요
        String portOneStatus = "PAID";
        String portOneMethod = "CARD";
        Long portOneAmount = payment.getAmount();
        LocalDateTime portOnePaidAt = LocalDateTime.now();

        if (!payment.getAmount().equals(portOneAmount)) {
            // TODO: PortOne 결제 취소 API 호출
            payment.fail();
            order.failPayment();
            throw new PaymentException(ErrorCode.PAYMENT_AMOUNT_MISMATCH);
        }

        if ("PAID".equals(portOneStatus)) {
            payment.complete(portOneMethod, portOnePaidAt);
            order.completePayment();
        } else {
            payment.fail();
            order.failPayment();
        }

        return PaymentResponse.from(payment);
    }

    /**
     * 6.4 PortOne Webhook 수신
     * PG 직접결제와 빌링키 자동결제 모두 이 Webhook으로 수신.
     * Client Confirm과 멱등성을 공유하며, PortOne이 200을 받지 못하면 재전송하므로
     * 처리 결과와 무관하게 서명 검증만 통과하면 200을 반환한다.
     */
    public void handleWebhook(String signature, WebhookRequest request) {
        // TODO: X-PortOne-Signature 서명 검증
        //   - PortOne 제공 라이브러리 또는 HMAC-SHA256으로 검증
        //   - 검증 실패 시 즉시 예외 반환 (ErrorCode.WEBHOOK_SIGNATURE_INVALID)
        // if (!portOneSignatureVerifier.verify(signature, rawBody)) {
        //     throw new PaymentException(ErrorCode.WEBHOOK_SIGNATURE_INVALID);
        // }

        String paymentId = request.data().paymentId();

        // PortOne은 200을 받아야 재전송을 멈추므로 알 수 없는 paymentId도 조용히 처리
        Payment payment = paymentRepository.findByPaymentUid(paymentId).orElse(null);
        if (payment == null) {
            return;
        }

        // 멱등성: 이미 최종 상태(COMPLETED / FAILED / REFUNDED)면 스킵
        if (isFinalized(payment.getStatus())) {
            return;
        }

        // TODO: PortOne API 재조회 (2차 검증) → GET https://api.portone.io/v2/payments/{paymentId}
        //   - webhook body만 신뢰하지 않고 반드시 API 재조회로 상태·금액 확인
        Long portOneAmount = request.data().amount().total();
        String portOneStatus = request.data().status();
        String portOneMethod = "CARD";                     // TODO: API 재조회에서 가져오기
        LocalDateTime portOnePaidAt = LocalDateTime.now(); // TODO: API 재조회에서 가져오기

        Order order = findOrder(payment.getOrderId());

        if ("PAID".equals(portOneStatus)) {
            if (!payment.getAmount().equals(portOneAmount)) {
                // TODO: PortOne 결제 취소 API 호출
                payment.fail();
                order.failPayment();
                return;
            }
            payment.complete(portOneMethod, portOnePaidAt);
            order.completePayment();
        } else {
            payment.fail();
            order.failPayment();
        }
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

    private void validateBuyer(Order order, Long requesterId) {
        if (!order.getBuyerId().equals(requesterId)) {
            throw new PaymentException(ErrorCode.PAYMENT_BUYER_MISMATCH);
        }
    }

    private boolean isFinalized(PaymentStatus status) {
        return status == PaymentStatus.COMPLETED
                || status == PaymentStatus.FAILED
                || status == PaymentStatus.REFUNDED;
    }

    private String generatePaymentUid() {
        return "pocat-payment-" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
    }
}
