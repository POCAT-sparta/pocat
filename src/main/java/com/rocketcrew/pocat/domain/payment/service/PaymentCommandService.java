package com.rocketcrew.pocat.domain.payment.service;

import com.fasterxml.jackson.databind.ObjectMapper;
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

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional
public class PaymentCommandService {

    private final PaymentRepository paymentRepository;
    private final OrderRepository orderRepository;
    private final ObjectMapper objectMapper;
    private final PaymentFailureService paymentFailureService;

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
        //   ① 응답 status가 "PAID"인지 확인
        //   ② 응답 amount.total이 payment.getAmount()와 일치하는지 검증
        //   ③ 불일치 시: PortOne 결제 취소 API 호출 후
        //      paymentFailureService.markFailed(payment.getId(), order.getId()) 호출 (REQUIRES_NEW)
        //      → 외부 트랜잭션 롤백과 무관하게 실패 상태가 커밋됨
        //      → 이후 throw new PaymentException(ErrorCode.PAYMENT_AMOUNT_MISMATCH)
        //   ④ 검증 통과 시: payment.complete(method, paidAt), order.completePayment() 호출
        //
        // [fail-closed] PortOne 연동 완료 전까지 이 경로는 차단한다.
        // 하드코딩된 플레이스홀더로 결제가 우회 완료되는 보안 취약점을 방지.
        throw new PaymentException(ErrorCode.PORTONE_NOT_INTEGRATED);
    }

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

        // TODO: X-PortOne-Signature 서명 검증 (원본 바이트 rawBody 기준으로 HMAC-SHA256 검증)
        //   - PortOne 제공 라이브러리 또는 직접 HMAC-SHA256 구현
        //   - 검증 실패 시 즉시 예외 반환 (ErrorCode.WEBHOOK_SIGNATURE_INVALID)
        // if (!portOneSignatureVerifier.verify(signature, rawBody)) {
        //     throw new PaymentException(ErrorCode.WEBHOOK_SIGNATURE_INVALID);
        // }

        // [fail-closed] 서명 검증이 TODO인 동안은 상태 변경 경로 전체를 차단한다.
        // permitAll 엔드포인트이므로 위조 이벤트가 주문·결제 상태를 바꾸는 것을 방지.
        // 서명 검증 구현 완료 후 이 블록을 제거하고 아래 처리 로직을 활성화한다.
        throw new PaymentException(ErrorCode.PORTONE_NOT_INTEGRATED);

        // ── 아래는 서명 검증 완료 후 활성화 ────────────────────────────────────

        /*
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
        // 아래 로직은 서명 검증 완료 후 주석 해제
        // String paymentId = request.data().paymentId();
        // Payment payment = paymentRepository.findByPaymentUid(paymentId).orElse(null);
        // if (payment == null) { return; }
        // if (isFinalized(payment.getStatus())) { return; }
        // TODO: PortOne API 재조회 → 상태·금액 2차 검증
        // Order order = findOrder(payment.getOrderId());
        // if ("PAID".equals(portOneStatus)) {
        //     if (!payment.getAmount().equals(portOneAmount)) {
        //         paymentFailureService.markFailed(payment.getId(), order.getId()); // REQUIRES_NEW
        //         return;
        //     }
        //     payment.complete(portOneMethod, portOnePaidAt);
        //     order.completePayment();
        // } else {
        //     paymentFailureService.markFailed(payment.getId(), order.getId()); // REQUIRES_NEW
        // }
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
