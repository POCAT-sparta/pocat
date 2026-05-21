package com.rocketcrew.pocat.domain.payment.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rocketcrew.pocat.domain.order.entity.Order;
import com.rocketcrew.pocat.domain.order.enums.OrderStatus;
import com.rocketcrew.pocat.domain.order.repository.OrderRepository;
import com.rocketcrew.pocat.domain.payment.client.PortOneClient;
import com.rocketcrew.pocat.domain.payment.client.PortOnePaymentResponse;
import com.rocketcrew.pocat.domain.payment.client.PortOneSignatureVerifier;
import com.rocketcrew.pocat.domain.payment.dto.request.CreatePaymentRequest;
import com.rocketcrew.pocat.domain.payment.dto.request.WebhookRequest;
import com.rocketcrew.pocat.domain.payment.dto.response.PaymentResponse;
import com.rocketcrew.pocat.domain.payment.entity.Payment;
import com.rocketcrew.pocat.domain.payment.entity.PaymentStatus;
import com.rocketcrew.pocat.domain.payment.entity.PaymentType;
import com.rocketcrew.pocat.domain.payment.entity.WebhookEvent;
import com.rocketcrew.pocat.domain.payment.entity.WebhookEventStatus;
import com.rocketcrew.pocat.domain.payment.repository.PaymentRepository;
import com.rocketcrew.pocat.domain.payment.repository.WebhookEventRepository;
import com.rocketcrew.pocat.domain.settlement.service.SettlementCommandService;
import com.rocketcrew.pocat.domain.user.entity.User;
import com.rocketcrew.pocat.domain.user.repository.UserRepository;
import com.rocketcrew.pocat.global.exception.common.ErrorCode;
import com.rocketcrew.pocat.global.exception.domain.OrderException;
import com.rocketcrew.pocat.global.exception.domain.PaymentException;
import com.rocketcrew.pocat.global.exception.domain.UserException;
import com.rocketcrew.pocat.global.util.TsidGenerator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class PaymentCommandService {

    private final PaymentRepository paymentRepository;
    private final OrderRepository orderRepository;
    private final ObjectMapper objectMapper;
    private final PaymentFailureService paymentFailureService;
    private final SettlementCommandService settlementCommandService;
    private final UserRepository userRepository;
    private final PortOneClient portOneClient;
    private final PortOneSignatureVerifier portOneSignatureVerifier;
    private final WebhookEventRepository webhookEventRepository;

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

        if (order.getUpdatedAt().plusHours(1).isBefore(LocalDateTime.now())) {
            throw new PaymentException(ErrorCode.PAYMENT_WINDOW_EXPIRED);
        }

        Optional<Payment> existing = paymentRepository.findByOrderIdAndStatus(
                request.orderId(), PaymentStatus.PENDING);
        if (existing.isPresent()) {
            Payment existingPayment = existing.get();
            // 생성 후 1시간 이상 지난 PENDING은 만료 처리 후 새로 발급
            if (existingPayment.getCreatedAt().plusHours(1).isAfter(LocalDateTime.now())) {
                return PaymentResponse.from(existingPayment);
            }
            existingPayment.fail();
        }

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
     *
     * 방어 포인트:
     * - PortOne API 호출은 lock 없이 수행 (lock 보유 중 네트워크 대기 방지)
     * - 실패 경로에서는 lock 없이 REQUIRES_NEW 서비스 호출 (데드락 방지)
     * - 금액 불일치 시 PortOne 결제를 보상 취소하여 사용자 피해 방지
     * - 성공 경로: lock 획득 후 재검증 (webhook과의 레이스 컨디션 방지)
     * - PortOne 응답 필드(amount, method) null 방어
     */
    public PaymentResponse confirmPayment(Long buyerId, String paymentUid) {
        Payment payment = findPaymentByUid(paymentUid);
        Order order = findOrder(payment.getOrderId());

        validateBuyer(order, buyerId);

        if (isFinalized(payment.getStatus())) return PaymentResponse.from(payment);

        // PortOne 조회 — lock 없이, 내부 3회 재시도 포함
        PortOnePaymentResponse portOnePayment = portOneClient.getPayment(paymentUid);

        // ── 실패 경로: lock 없이 REQUIRES_NEW 호출 ──────────────────────────────
        if (!"PAID".equals(portOnePayment.status())) {
            paymentFailureService.markFailed(payment.getId());
            throw new PaymentException(ErrorCode.PAYMENT_STATUS_NOT_PAID);
        }

        // PortOne 응답 amount null 방어
        if (portOnePayment.amount() == null) {
            log.error("PortOne 응답 amount null paymentUid={}", paymentUid);
            paymentFailureService.markFailed(payment.getId());
            throw new PaymentException(ErrorCode.PORTONE_NOT_INTEGRATED);
        }

        // 금액 불일치 — PortOne 결제 즉시 취소(보상 트랜잭션)
        if (!payment.getAmount().equals(portOnePayment.amount())) {
            log.error("결제 금액 불일치 paymentUid={} expected={} actual={}",
                    paymentUid, payment.getAmount(), portOnePayment.amount());
            cancelSilently(paymentUid, portOnePayment.amount(), "금액 불일치 자동 취소");
            paymentFailureService.markFailed(payment.getId());
            throw new PaymentException(ErrorCode.PAYMENT_AMOUNT_MISMATCH);
        }

        // PortOne 응답 paymentMethod null 방어
        if (portOnePayment.paymentMethod() == null || portOnePayment.paymentMethod().isBlank()) {
            log.error("PortOne 응답 paymentMethod null paymentUid={}", paymentUid);
            paymentFailureService.markFailed(payment.getId());
            throw new PaymentException(ErrorCode.PORTONE_NOT_INTEGRATED);
        }

        // ── 성공 경로: lock 획득 후 재검증 ────────────────────────────────────────
        payment = findPaymentByUidWithLock(paymentUid);
        if (isFinalized(payment.getStatus())) return PaymentResponse.from(payment);

        try {
            payment.complete(portOnePayment.paymentMethod(), portOnePayment.paidAt());
            order.completePayment();
            settlementCommandService.createSettlement(order.getOrderUid());
        } catch (Exception e) {
            // DB 저장 실패 — PortOne 결제 보상 취소 후 롤백
            log.error("결제 확정 DB 저장 실패, 보상 취소 시작 paymentUid={}", paymentUid, e);
            cancelSilently(paymentUid, portOnePayment.amount(), "서버 오류 자동 취소");
            throw e;
        }

        return PaymentResponse.from(payment);
    }

    /**
     * 6.4 PortOne Webhook 수신
     *
     * 방어 포인트:
     * - 서명 null 즉시 거부
     * - 빌링키 이벤트 등 비결제 웹훅 200 처리
     * - PAID 이벤트: PortOne 재조회를 lock 획득 전에 수행 (lock 보유 중 네트워크 대기 방지)
     * - 금액/method null 방어
     * - 금액 불일치 시 보상 취소
     * - 멱등성: (paymentId, status) 복합 키로 중복 처리 방지
     */
    public void handleWebhook(String signature, byte[] rawBody) {
        if (rawBody == null || rawBody.length == 0) {
            throw new PaymentException(ErrorCode.WEBHOOK_EMPTY_BODY);
        }

        if (signature == null || !portOneSignatureVerifier.verify(signature, rawBody)) {
            throw new PaymentException(ErrorCode.WEBHOOK_SIGNATURE_INVALID);
        }

        WebhookRequest request;
        try {
            request = objectMapper.readValue(rawBody, WebhookRequest.class);
        } catch (IOException e) {
            throw new PaymentException(ErrorCode.WEBHOOK_PARSE_FAILED);
        }

        // 결제 이벤트가 아닌 웹훅(빌링키 발급/삭제 등) — 200으로 정상 수신 처리
        if (request.data() == null || request.data().paymentId() == null) {
            return;
        }

        if (request.data().status() == null
                || request.data().amount() == null
                || request.data().amount().total() == null) {
            throw new PaymentException(ErrorCode.WEBHOOK_INVALID_PAYLOAD);
        }

        String paymentId = request.data().paymentId();
        String status = request.data().status();

        // PAID 이벤트: PortOne 재조회를 lock 획득 전에 수행 (네트워크 대기 중 lock 보유 방지)
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

        // 멱등성 체크 (lock 전)
        if (webhookEventRepository.findByPaymentIdAndEventType(paymentId, status).isPresent()) {
            return;
        }

        // lock 획득
        Payment payment = paymentRepository.findByPaymentUidWithLock(paymentId).orElse(null);
        if (payment == null || isFinalized(payment.getStatus())) {
            return;
        }

        // TOCTOU 방어: 멱등성 체크 통과 후 동시 중복 웹훅이 들어오면 UNIQUE 제약 위반
        // DataIntegrityViolationException을 잡아 멱등 처리 (예외 전파 시 전체 트랜잭션 롤백되어 정산까지 유실)
        WebhookEvent webhookEvent;
        try {
            webhookEvent = webhookEventRepository.save(WebhookEvent.builder()
                    .paymentId(paymentId)
                    .eventType(status)
                    .rawBody(new String(rawBody, StandardCharsets.UTF_8))
                    .status(WebhookEventStatus.RECEIVED)
                    .build());
        } catch (DataIntegrityViolationException e) {
            log.info("웹훅 이벤트 중복 저장 감지, 멱등 처리 paymentId={} status={}", paymentId, status);
            return;
        }

        Order order = findOrder(payment.getOrderId());

        if ("PAID".equals(status)) {
            // portOnePayment는 위에서 반드시 세팅됨
            if (portOnePayment.amount() == null || portOnePayment.paymentMethod() == null
                    || portOnePayment.paymentMethod().isBlank()) {
                log.error("웹훅 PortOne 응답 필드 누락 paymentId={} amount={} method={}",
                        paymentId, portOnePayment.amount(), portOnePayment.paymentMethod());
                payment.fail();
                order.failPayment();
                webhookEvent.markFailed();
                return;
            }

            if (!payment.getAmount().equals(portOnePayment.amount())) {
                log.error("웹훅 금액 불일치, 보상 취소 시작 paymentId={} expected={} actual={}",
                        paymentId, payment.getAmount(), portOnePayment.amount());
                cancelSilently(paymentId, portOnePayment.amount(), "금액 불일치 자동 취소");
                payment.fail();
                order.failPayment();
                webhookEvent.markFailed();
                return;
            }

            payment.complete(portOnePayment.paymentMethod(), portOnePayment.paidAt());
            order.completePayment();
            settlementCommandService.createSettlement(order.getOrderUid());
        } else if ("CANCELLED".equals(status)) {
            // 사용자가 결제창에서 직접 취소 — FAILED와 동일한 상태로 처리하되 로그 분리
            log.info("결제창 사용자 취소 웹훅 수신 paymentId={}", paymentId);
            payment.fail();
            order.failPayment();
        } else {
            // FAILED 또는 미지원 상태 — 결제 실패 처리
            log.info("결제 실패 웹훅 수신 paymentId={} status={}", paymentId, status);
            payment.fail();
            order.failPayment();
        }

        webhookEvent.markProcessed();
    }

    /**
     * 빌링키 자동결제.
     *
     * 방어 포인트:
     * - PortOne 응답 필드(amount, method) null 방어
     * - 금액 불일치 시 즉시 보상 취소
     * - DB 저장 실패 시 보상 취소 (보상 실패 시 로그 후 수동 처리)
     */
    public PaymentResponse attemptBillingKeyPayment(Long orderId) {
        Order order = findOrder(orderId);

        if (order.getStatus() == OrderStatus.PAYMENT_COMPLETED) {
            return paymentRepository.findByOrderId(order.getId())
                    .map(PaymentResponse::from)
                    .orElseThrow(() -> new PaymentException(ErrorCode.PAYMENT_NOT_FOUND));
        }

        User user = findUser(order.getBuyerId());
        String billingKey = user.getBillingKey();

        if (billingKey == null || billingKey.isEmpty()) {
            throw new PaymentException(ErrorCode.BILLING_KEY_NOT_FOUND);
        }

        Payment payment = paymentRepository.findByOrderIdAndStatus(order.getId(), PaymentStatus.PENDING)
                .orElseGet(() -> paymentRepository.save(Payment.builder()
                        .orderId(orderId)
                        .paymentUid(TsidGenerator.generatePaymentUid())
                        .amount(order.getFinalPrice())
                        .paymentType(PaymentType.BILLING_KEY)
                        .status(PaymentStatus.PENDING)
                        .build())
                );

        PortOnePaymentResponse response = portOneClient.attemptBillingKeyPayment(
                payment.getPaymentUid(), billingKey, payment.getAmount()
        );

        if (!"PAID".equals(response.status())) {
            payment.fail();
            order.failPayment();
            return PaymentResponse.from(payment);
        }

        // PortOne 응답 amount null 방어
        if (response.amount() == null) {
            log.error("빌링키 결제 응답 amount null paymentUid={}", payment.getPaymentUid());
            cancelSilently(payment.getPaymentUid(), payment.getAmount(), "응답 amount null 자동 취소");
            payment.fail();
            order.failPayment();
            return PaymentResponse.from(payment);
        }

        // 금액 불일치 — 보상 취소
        if (!payment.getAmount().equals(response.amount())) {
            log.error("빌링키 결제 금액 불일치, 보상 취소 시작 paymentUid={} expected={} actual={}",
                    payment.getPaymentUid(), payment.getAmount(), response.amount());
            cancelSilently(payment.getPaymentUid(), response.amount(), "금액 불일치 자동 취소");
            payment.fail();
            order.failPayment();
            return PaymentResponse.from(payment);
        }

        // PortOne 응답 paymentMethod null 방어
        if (response.paymentMethod() == null || response.paymentMethod().isBlank()) {
            log.error("빌링키 결제 응답 paymentMethod null paymentUid={}", payment.getPaymentUid());
            cancelSilently(payment.getPaymentUid(), response.amount(), "응답 paymentMethod null 자동 취소");
            payment.fail();
            order.failPayment();
            return PaymentResponse.from(payment);
        }

        // DB 저장 실패 시 보상 취소
        try {
            payment.complete(response.paymentMethod(), response.paidAt());
            order.completePayment();
            settlementCommandService.createSettlement(order.getOrderUid());
        } catch (Exception e) {
            log.error("빌링키 결제 후 DB 저장 실패, 보상 취소 시작 paymentUid={}", payment.getPaymentUid(), e);
            cancelSilently(payment.getPaymentUid(), payment.getAmount(), "서버 오류 자동 취소");
            throw e;
        }

        return PaymentResponse.from(payment);
    }

    // ── 내부 헬퍼 ────────────────────────────────────────────────────

    /**
     * 보상 취소를 시도하되 실패해도 예외를 전파하지 않는다.
     * 실패 시 로그만 남기고 수동 처리를 기다린다.
     */
    private void cancelSilently(String paymentUid, Long amount, String reason) {
        try {
            portOneClient.cancelPayment(paymentUid, amount, reason);
        } catch (Exception e) {
            log.error("보상 취소 실패 — 수동 처리 필요 paymentUid={}", paymentUid, e);
        }
    }

    private Payment findPaymentByUid(String paymentUid) {
        return paymentRepository.findByPaymentUid(paymentUid)
                .orElseThrow(() -> new PaymentException(ErrorCode.PAYMENT_NOT_FOUND));
    }

    private Payment findPaymentByUidWithLock(String paymentUid) {
        return paymentRepository.findByPaymentUidWithLock(paymentUid)
                .orElseThrow(() -> new PaymentException(ErrorCode.PAYMENT_NOT_FOUND));
    }

    private Order findOrder(Long orderId) {
        return orderRepository.findById(orderId)
                .orElseThrow(() -> new OrderException(ErrorCode.ORDER_NOT_FOUND));
    }

    private User findUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new UserException(ErrorCode.USER_NOT_FOUND));
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
        return TsidGenerator.generatePaymentUid();
    }
}
