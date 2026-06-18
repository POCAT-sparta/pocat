package com.rocketcrew.pocat.domain.refund.service;

import com.rocketcrew.pocat.domain.order.entity.Order;
import com.rocketcrew.pocat.domain.order.enums.OrderStatus;
import com.rocketcrew.pocat.domain.order.repository.OrderRepository;
import com.rocketcrew.pocat.domain.payment.client.out.portone.PortOneClientService;
import com.rocketcrew.pocat.domain.payment.entity.Payment;
import com.rocketcrew.pocat.domain.payment.entity.PaymentStatus;
import com.rocketcrew.pocat.domain.payment.repository.PaymentRepository;
import com.rocketcrew.pocat.domain.refund.dto.request.CreateRefundRequest;
import com.rocketcrew.pocat.domain.refund.dto.request.RejectRefundRequest;
import com.rocketcrew.pocat.domain.refund.dto.response.RefundResponse;
import com.rocketcrew.pocat.domain.refund.entity.Refund;
import com.rocketcrew.pocat.domain.refund.entity.RefundStatus;
import com.rocketcrew.pocat.domain.refund.repository.RefundRepository;
import com.rocketcrew.pocat.domain.settlement.entity.Settlement;
import com.rocketcrew.pocat.domain.settlement.repository.SettlementRepository;
import com.rocketcrew.pocat.global.exception.common.ErrorCode;
import com.rocketcrew.pocat.global.exception.domain.OrderException;
import com.rocketcrew.pocat.global.exception.domain.PaymentException;
import com.rocketcrew.pocat.domain.refund.event.RefundApprovedEvent;
import com.rocketcrew.pocat.domain.refund.event.RefundRejectedEvent;
import com.rocketcrew.pocat.domain.refund.event.RefundRequestedEvent;
import com.rocketcrew.pocat.global.exception.domain.RefundException;
import com.rocketcrew.pocat.global.exception.domain.SettlementException;
import com.rocketcrew.pocat.global.outbox.service.OutboxEventWriter;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class RefundCommandService {

    private final RefundRepository refundRepository;
    private final OrderRepository orderRepository;
    private final PaymentRepository paymentRepository;
    private final SettlementRepository settlementRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final PortOneClientService portOneClientService;
    private final OutboxEventWriter outboxEventWriter;

    // 환불 요청 가능한 주문 상태
    private static final Set<OrderStatus> REFUNDABLE_STATUSES =
            EnumSet.of(OrderStatus.PAYMENT_COMPLETED, OrderStatus.ORDER_COMPLETED);

    // 중복 환불 차단 대상 상태 (활성 환불이 있으면 새로 만들지 않음)
    private static final List<RefundStatus> ACTIVE_REFUND_STATUSES = List.of(
            RefundStatus.REQUESTED,
            RefundStatus.PROCESSING,
            RefundStatus.FAILED_RETRYABLE,
            RefundStatus.COMPLETED
    );

    /**
     * 7.1 환불 요청
     * - 결제 완료된 주문만 가능 (PAYMENT_COMPLETED / ORDER_COMPLETED)
     * - 동일 주문에 활성 환불이 이미 있으면 중복 요청 차단
     * - 환불 금액은 payments.amount 전액 자동 적용 (클라이언트 금액 신뢰 금지)
     */
    public RefundResponse createRefund(Long buyerId, CreateRefundRequest request) {
        Order order = orderRepository.findByIdWithLock(request.orderId())
                .orElseThrow(() -> new OrderException(ErrorCode.ORDER_NOT_FOUND));

        if (!order.getBuyerId().equals(buyerId)) {
            throw new RefundException(ErrorCode.REFUND_BUYER_MISMATCH);
        }

        if (!REFUNDABLE_STATUSES.contains(order.getStatus())) {
            throw new RefundException(ErrorCode.REFUND_INVALID_ORDER_STATUS);
        }

        if (refundRepository.existsByOrderIdAndStatusIn(request.orderId(), ACTIVE_REFUND_STATUSES)) {
            throw new RefundException(ErrorCode.REFUND_ALREADY_EXISTS);
        }

        Payment payment = paymentRepository.findByOrderIdAndStatus(request.orderId(), PaymentStatus.COMPLETED)
                .orElseThrow(() -> new PaymentException(ErrorCode.PAYMENT_NOT_FOUND));

        Refund refund = Refund.builder()
                .orderId(request.orderId())
                .paymentId(payment.getId())
                .amount(payment.getAmount())
                .reason(request.reason())
                .status(RefundStatus.REQUESTED)
                .build();

        Refund saved = refundRepository.save(refund);
        RefundRequestedEvent requestedEvent = new RefundRequestedEvent(
                saved.getId(), order.getOrderUid(), order.getBuyerId());
        outboxEventWriter.write("refund", order.getOrderUid(), requestedEvent);
        eventPublisher.publishEvent(requestedEvent);
        return RefundResponse.from(saved);
    }

    /**
     * 7.4 환불 승인 (ADMIN)
     * PROCESSING 마킹 → PortOne 취소 API 호출 → 성공 시 COMPLETED / 실패 시 FAILED_RETRYABLE.
     * 실패 시 스케줄러가 지수 백오프로 자동 재시도한다.
     *
     * 주의: PortOne 취소 성공 후 DB 트랜잭션 실패 시, 재시도하면 PortOne에 중복 취소 요청이 발생할 수 있다.
     * 추후 PortOne 멱등성 키(idempotency key) 적용을 권장한다.
     */
    public RefundResponse approveRefund(Long refundId) {
        Refund refund = findRefund(refundId);
        validateRefundRequested(refund);

        Order order = findOrder(refund.getOrderId());
        Payment payment = paymentRepository.findById(refund.getPaymentId())
                .orElseThrow(() -> new PaymentException(ErrorCode.PAYMENT_NOT_FOUND));
        Settlement settlement = settlementRepository.findByOrderId(refund.getOrderId())
                .orElseThrow(() -> new SettlementException(ErrorCode.SETTLEMENT_NOT_FOUND));

        refund.markProcessing();

        try {
            portOneClientService.cancelPayment(payment.getPaymentUid(), refund.getAmount(), refund.getReason());
        } catch (Exception e) {
            handleRetryFailure(refund, e.getMessage());
            log.warn("환불 승인 중 PortOne 취소 실패 — 자동 재시도 예정. refundId={}, paymentUid={}",
                    refundId, payment.getPaymentUid(), e);
            throw new RefundException(ErrorCode.REFUND_PORTONE_FAILED);
        }

        refund.markCompleted();
        payment.refund();
        order.refund();
        settlement.refund();

        RefundApprovedEvent approvedEvent = new RefundApprovedEvent(
                refund.getId(), order.getOrderUid(), order.getBuyerId(), order.getSellerId());
        outboxEventWriter.write("refund", order.getOrderUid(), approvedEvent);
        eventPublisher.publishEvent(approvedEvent);
        log.info("환불 승인 완료. refundId={}, paymentUid={}, amount={}",
                refundId, payment.getPaymentUid(), refund.getAmount());

        return RefundResponse.from(refund);
    }

    /**
     * 7.4-R 환불 재시도 (스케줄러 호출)
     * FAILED_RETRYABLE 상태의 환불을 재시도하여 PortOne 취소를 재시도한다.
     * 최대 횟수 초과 시 FAILED_FINAL로 전환 → 관리자 수동 처리 필요.
     */
    public void retryRefund(Long refundId) {
        Refund refund = refundRepository.findByIdWithLock(refundId)
                .orElseThrow(() -> new RefundException(ErrorCode.REFUND_NOT_FOUND));

        // FAILED_RETRYABLE: 정상 재시도 경로
        // PROCESSING: approveRefund에서 PortOne 취소 성공 후 DB 실패로 방치된 경로
        if (refund.getStatus() != RefundStatus.FAILED_RETRYABLE
                && refund.getStatus() != RefundStatus.PROCESSING) {
            log.info("재시도 대상 상태 아님. refundId={}, status={}", refundId, refund.getStatus());
            return;
        }

        // FAILED_RETRYABLE은 nextRetryAt 도래 여부 확인 (PROCESSING은 즉시 재시도)
        if (refund.getStatus() == RefundStatus.FAILED_RETRYABLE
                && !refund.isRetryDue(LocalDateTime.now())) {
            log.info("아직 재시도 시간 아님. refundId={}, nextRetryAt={}", refundId, refund.getNextRetryAt());
            return;
        }

        // 동시 처리 직렬화를 위해 관련 엔티티도 비관적 락 획득
        Payment payment = paymentRepository.findByIdWithLock(refund.getPaymentId())
                .orElseThrow(() -> new PaymentException(ErrorCode.PAYMENT_NOT_FOUND));
        Order order = orderRepository.findByIdWithLock(refund.getOrderId())
                .orElseThrow(() -> new OrderException(ErrorCode.ORDER_NOT_FOUND));
        Settlement settlement = settlementRepository.findByOrderIdWithLock(refund.getOrderId())
                .orElseThrow(() -> new SettlementException(ErrorCode.SETTLEMENT_NOT_FOUND));

        refund.markProcessing();

        log.info("환불 재시도 시작. refundId={}, paymentUid={}, retryCount={}",
                refundId, payment.getPaymentUid(), refund.getRetryCount());

        try {
            portOneClientService.cancelPayment(payment.getPaymentUid(), refund.getAmount(), refund.getReason());
        } catch (Exception e) {
            handleRetryFailure(refund, e.getMessage());
            log.warn("환불 재시도 실패. refundId={}, paymentUid={}, retryCount={}",
                    refundId, payment.getPaymentUid(), refund.getRetryCount(), e);
            return;
        }

        refund.markCompleted();
        payment.refund();
        order.refund();
        settlement.refund();

        RefundApprovedEvent approvedEvent = new RefundApprovedEvent(
                refund.getId(), order.getOrderUid(), order.getBuyerId(), order.getSellerId());
        outboxEventWriter.write("refund", order.getOrderUid(), approvedEvent);
        eventPublisher.publishEvent(approvedEvent);

        log.info("환불 재시도 성공. refundId={}, paymentUid={}", refundId, payment.getPaymentUid());
    }

    /**
     * 7.5 환불 거절 (ADMIN)
     */
    public RefundResponse rejectRefund(Long refundId, RejectRefundRequest request) {
        Refund refund = findRefund(refundId);
        validateRefundRequested(refund);

        Order order = findOrder(refund.getOrderId());
        refund.reject(request.rejectReason());
        RefundRejectedEvent rejectedEvent = new RefundRejectedEvent(
                refund.getId(), order.getOrderUid(), order.getBuyerId(), request.rejectReason());
        outboxEventWriter.write("refund", order.getOrderUid(), rejectedEvent);
        eventPublisher.publishEvent(rejectedEvent);
        return RefundResponse.from(refund);
    }

    // ── 내부 헬퍼 ────────────────────────────────────────────────────

    private void handleRetryFailure(Refund refund, String reason) {
        int currentCount = refund.getRetryCount();
        if (RefundRetryPolicy.isAutoRetryExhausted(currentCount)) {
            refund.markFinalFailure(reason);
            log.error("환불 자동 재시도 한도 초과 — 수동 처리 필요. refundId={}, retryCount={}",
                    refund.getId(), currentCount);
        } else {
            LocalDateTime nextRetryAt = RefundRetryPolicy.calculateNextRetryAt(currentCount, LocalDateTime.now());
            refund.markRetryableFailure(reason, nextRetryAt);
        }
    }

    private Refund findRefund(Long refundId) {
        return refundRepository.findByIdWithLock(refundId)
                .orElseThrow(() -> new RefundException(ErrorCode.REFUND_NOT_FOUND));
    }

    private Order findOrder(Long orderId) {
        return orderRepository.findById(orderId)
                .orElseThrow(() -> new OrderException(ErrorCode.ORDER_NOT_FOUND));
    }

    private void validateRefundRequested(Refund refund) {
        if (refund.getStatus() != RefundStatus.REQUESTED) {
            throw new RefundException(ErrorCode.REFUND_NOT_REQUESTED);
        }
    }
}
