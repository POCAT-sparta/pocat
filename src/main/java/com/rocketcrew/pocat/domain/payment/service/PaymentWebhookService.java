package com.rocketcrew.pocat.domain.payment.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rocketcrew.pocat.domain.order.entity.Order;
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
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;

@Slf4j
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
     *
     * 방어 포인트:
     * - rawBody null/empty 즉시 거부
     * - 서명 null·불일치 즉시 거부
     * - 빌링키 이벤트 등 비결제 웹훅은 200 정상 처리 (은폐 방지를 위해 명시적 return)
     * - 금액 불일치 시 failureService로 실패 처리 (보상 취소는 PortOne 측에서 처리됨)
     * - isFinalized() 체크로 중복 웹훅 멱등 처리
     */
    @Transactional
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

        // ── 서명 검증 완료 후 처리 ────────────────────────────────────────────
        WebhookRequest request;
        try {
            request = objectMapper.readValue(rawBody, WebhookRequest.class);
        } catch (IOException e) {
            // 파싱 실패는 PortOne이 보낸 포맷이 아닌 경우 — non-200 반환 (재전송 유도)
            throw new PaymentException(ErrorCode.WEBHOOK_PARSE_FAILED);
        }

        // 결제 이벤트가 아닌 웹훅(빌링키 발급/삭제 등) — 200으로 정상 수신 처리
        // 이 이벤트들은 payment 도메인이 처리할 대상이 아니므로 무시한다.
        if (request.data() == null || request.data().paymentId() == null) {
            log.debug("비결제 웹훅 수신 (빌링키 이벤트 등) — 200 반환");
            return;
        }

        // 핵심 필드 null 가드 — 서비스 내 역직렬화이므로 Bean Validation 자동 실행 안 됨
        if (request.data().status() == null
                || request.data().amount() == null
                || request.data().amount().total() == null) {
            throw new PaymentException(ErrorCode.WEBHOOK_INVALID_PAYLOAD);
        }

        String paymentId = request.data().paymentId();
        String status = request.data().status();

        Payment payment = paymentRepository.findByPaymentUidWithLock(paymentId).orElse(null);

        if (payment == null) {
            log.info("웹훅 수신 — 대응하는 결제 없음 paymentId={}", paymentId);
            return;
        }

        if (payment.isFinalized()) {
            log.info("웹훅 수신 — 이미 최종 상태 처리 완료 paymentId={} status={}", paymentId, payment.getStatus());
            return;
        }

        if ("PAID".equals(status)) {
            Order order = orderQueryService.findByOrderid(payment.getOrderId());

            PortOnePaymentResponse portOneClientPayment = portOneClient.getPayment(paymentId);
            Long paidAmount = portOneClientPayment.amount();

            if (paidAmount == null) {
                log.error("웹훅 PortOne 응답 amount null paymentId={}", paymentId);
                failureService.markFailed(payment.getOrderId());
                failureService.cancelExpiry(payment.getOrderId());
                return;
            }

            if (!payment.getAmount().equals(paidAmount)) {
                log.error("웹훅 금액 불일치 paymentId={} expected={} actual={}",
                        paymentId, payment.getAmount(), paidAmount);
                failureService.markFailed(payment.getOrderId());
                failureService.cancelExpiry(payment.getOrderId());
                throw new PaymentException(ErrorCode.PAYMENT_AMOUNT_MISMATCH);
            }

            paymentCommandService.completePayment(
                    payment, order,
                    portOneClientPayment.paymentMethod(), portOneClientPayment.paidAt()
            );
        } else if ("CANCELLED".equals(status)) {
            log.info("결제창 사용자 취소 웹훅 수신 paymentId={}", paymentId);
            failureService.markFailed(payment.getOrderId());
            failureService.cancelExpiry(payment.getOrderId());
        } else {
            // FAILED 또는 미지원 상태 — 결제 실패 처리
            log.info("결제 실패 웹훅 수신 paymentId={} status={}", paymentId, status);
            failureService.markFailed(payment.getOrderId());
            failureService.cancelExpiry(payment.getOrderId());
        }
    }
}
