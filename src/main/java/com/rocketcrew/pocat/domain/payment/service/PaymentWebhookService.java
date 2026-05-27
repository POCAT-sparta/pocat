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
     *
     * 방어 포인트:
     * - rawBody null/empty 즉시 거부
     * - 서명 null·불일치 즉시 거부
     * - 빌링키 이벤트 등 비결제 웹훅은 200 정상 처리
     * - PAID: PortOne 재조회를 락 획득 전에 수행 (락 보유 중 네트워크 대기 방지)
     * - 금액 불일치 시 failureService로 실패 처리
     * - isFinalized() 체크로 중복 웹훅 멱등 처리
     */
    @Transactional
    public void handleWebhook(String signature, byte[] rawBody) {
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
            // 파싱 실패 — non-200 반환으로 PortOne 재전송 유도
            throw new PaymentException(ErrorCode.WEBHOOK_PARSE_FAILED, e);
        }

        // objectMapper.readValue()는 JSON null 값에서 null을 반환할 수 있음
        if (request == null) {
            throw new PaymentException(ErrorCode.WEBHOOK_INVALID_PAYLOAD);
        }

        // 결제 이벤트가 아닌 웹훅(빌링키 발급/삭제 등) — 200으로 정상 수신 처리
        if (request.data() == null || request.data().paymentId() == null) {
            log.debug("비결제 웹훅 수신 (빌링키 이벤트 등) — 200 반환");
            return;
        }

        // 핵심 필드 null 가드
        if (request.data().status() == null
                || request.data().amount() == null
                || request.data().amount().total() == null) {
            throw new PaymentException(ErrorCode.WEBHOOK_INVALID_PAYLOAD);
        }

        String paymentId = request.data().paymentId();
        String status = request.data().status();

        // ── PAID: PortOne 재조회를 락 획득 전에 수행 ─────────────────────────
        // 락 보유 중 네트워크 I/O를 수행하면 락 점유 시간이 늘어나 동시 처리와 경합 시
        // 타임아웃·교착 위험이 커진다. 조회는 락 밖에서 먼저 수행하고,
        // 이후 짧은 트랜잭션으로 상태 확정만 처리한다.
        PortOnePaymentResponse portOnePayment = null;
        if ("PAID".equals(status)) {
            try {
                portOnePayment = portOneClient.getPayment(paymentId);
            } catch (Exception e) {
                // PortOne 조회 실패 — 재전송 유도 (non-200 반환)
                log.error("웹훅 처리 중 PortOne 조회 실패 paymentId={}", paymentId, e);
                throw new PaymentException(ErrorCode.PORTONE_NOT_INTEGRATED, e);
            }
        }

        // ── 락 획득 후 상태 확정 ─────────────────────────────────────────────
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
            // portOnePayment는 위에서 반드시 세팅됨
            Long paidAmount = portOnePayment.amount();

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
                return;  // 실패 처리 완료 — throw 시 non-200으로 PortOne 불필요 재전송 유발
            }

            Order order = orderQueryService.findByOrderid(payment.getOrderId());
            paymentCommandService.completePayment(
                    payment, order,
                    portOnePayment.paymentMethod(), portOnePayment.paidAt()
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
