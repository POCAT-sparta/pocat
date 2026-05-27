package com.rocketcrew.pocat.domain.payment.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rocketcrew.pocat.domain.order.entity.Order;
import com.rocketcrew.pocat.domain.order.repository.OrderRepository;
import com.rocketcrew.pocat.domain.order.service.OrderQueryService;
import com.rocketcrew.pocat.domain.payment.client.PortOneClient;
import com.rocketcrew.pocat.domain.payment.client.PortOnePaymentResponse;
import com.rocketcrew.pocat.domain.payment.client.PortOneSignatureVerifier;
import com.rocketcrew.pocat.domain.payment.dto.request.WebhookRequest;
import com.rocketcrew.pocat.domain.payment.entity.Payment;
import com.rocketcrew.pocat.domain.payment.repository.PaymentRepository;
import com.rocketcrew.pocat.global.exception.common.ErrorCode;
import com.rocketcrew.pocat.global.exception.domain.PaymentException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.io.IOException;

@Service
@RequiredArgsConstructor
public class PaymentWebhookService {

    private final PaymentRepository paymentRepository;
    private final ObjectMapper objectMapper;
    private final FailureService failureService;
    private final PaymentCommandService paymentCommandService;
    private final PortOneClient portOneClient;
    private final PortOneSignatureVerifier portOneSignatureVerifier;

    private final OrderQueryService orderQueryService;

    /**
     * 6.4 PortOne Webhook 수신
     * PG 직접결제와 빌링키 자동결제 모두 이 Webhook으로 수신.
     * rawBody(원본 바이트)를 받아 서명 검증 후 역직렬화하여 처리한다.
     * 재직렬화 시 바이트 불일치로 HMAC 검증이 실패하는 문제를 방지하기 위해
     * 컨트롤러에서 byte[]로 수신하고 서비스에서 ObjectMapper로 직접 역직렬화한다.
     */
    public void handleWebhook(String signature, byte[] rawBody) {
        // 빈 body는 PortOne이 보낸 정상 요청이 아니거나 인프라 설정 누락(필터가 body 소비 등)을 의미.
        // 조용히 200을 반환하면 문제가 은폐되므로 즉시 실패시켜 인지하도록 한다.
        // PortOne은 non-200 응답을 받으면 재전송하므로 인프라 수정 후 정상 처리된다.
        if (rawBody == null || rawBody.length == 0) {
            throw new PaymentException(ErrorCode.WEBHOOK_EMPTY_BODY);
        }

        if (signature == null || !portOneSignatureVerifier.verify(signature, rawBody)) {
            throw new PaymentException(ErrorCode.WEBHOOK_SIGNATURE_INVALID);
        }

        // ── 아래는 서명 검증 완료 후 활성화 ────────────────────────────────────
        WebhookRequest request;
        try {
            request = objectMapper.readValue(rawBody, WebhookRequest.class);
        } catch (IOException e) {
            // 파싱 실패는 PortOne이 보낸 포맷이 아닌 경우이므로 non-200 반환 (재전송 유도)
            throw new PaymentException(ErrorCode.WEBHOOK_EMPTY_BODY);
        }

        // 핵심 필드 null 가드 — 서비스 내 역직렬화이므로 Bean Validation 자동 실행 안 됨
        if (request.data() == null
                || request.data().paymentId() == null
                || request.data().status() == null
                || request.data().amount() == null
                || request.data().amount().total() == null) {
            throw new PaymentException(ErrorCode.WEBHOOK_EMPTY_BODY);
        }

        String paymentId = request.data().paymentId();
        String status = request.data().status();

        Payment payment = paymentRepository.findByPaymentUidWithLock(paymentId).orElse(null);

        if (payment == null) {
            return;
        }

        if (payment.isFinalized()) {
            return;
        }

        if ("PAID".equals(status)) {
            Order order = orderQueryService.findByOrderid(payment.getOrderId());

            PortOnePaymentResponse portOneClientPayment = portOneClient.getPayment(paymentId);
            Long amount = portOneClientPayment.amount();

            if (!payment.getAmount().equals(amount)) {
                failureService.markFailed(payment.getId());
                failureService.cancelExpiry(payment.getId());
                throw new PaymentException(ErrorCode.PAYMENT_AMOUNT_MISMATCH);
            }

            paymentCommandService.completePayment(
                    payment, order,
                    portOneClientPayment.paymentMethod(), portOneClientPayment.paidAt()
            );
        } else {
            failureService.markFailed(payment.getId());
            failureService.cancelExpiry(payment.getId());
        }
    }
}
