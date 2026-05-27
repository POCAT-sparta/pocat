package com.rocketcrew.pocat.domain.refund.service;

import com.rocketcrew.pocat.domain.order.entity.Order;
import com.rocketcrew.pocat.domain.order.enums.OrderStatus;
import com.rocketcrew.pocat.domain.order.repository.OrderRepository;
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
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Transactional
public class RefundCommandService {

    private final RefundRepository refundRepository;
    private final OrderRepository orderRepository;
    private final PaymentRepository paymentRepository;
    private final SettlementRepository settlementRepository;
    private final ApplicationEventPublisher eventPublisher;

    // 환불 요청 가능한 주문 상태
    private static final Set<OrderStatus> REFUNDABLE_STATUSES =
            EnumSet.of(OrderStatus.PAYMENT_COMPLETED, OrderStatus.SHIPPING, OrderStatus.ORDER_COMPLETED);

    /**
     * 7.1 환불 요청
     * - 결제 완료된 주문만 가능 (PAYMENT_COMPLETED / SHIPPING / COMPLETED)
     * - 동일 주문에 REQUESTED·COMPLETED 환불이 이미 있으면 중복 요청 차단
     * - 환불 금액은 payments.amount 전액 자동 적용 (클라이언트 금액 신뢰 금지)
     */
    public RefundResponse createRefund(Long buyerId, CreateRefundRequest request) {
        // 비관적 락으로 동일 주문에 대한 동시 환불 요청 직렬화
        Order order = orderRepository.findByIdWithLock(request.orderId())
                .orElseThrow(() -> new OrderException(ErrorCode.ORDER_NOT_FOUND));

        // 요청자 = 주문 구매자 검증
        if (!order.getBuyerId().equals(buyerId)) {
            throw new RefundException(ErrorCode.REFUND_BUYER_MISMATCH);
        }

        // 환불 가능 주문 상태 검증
        if (!REFUNDABLE_STATUSES.contains(order.getStatus())) {
            throw new RefundException(ErrorCode.REFUND_INVALID_ORDER_STATUS);
        }

        // 중복 환불 방지: 이미 REQUESTED 또는 COMPLETED 환불 존재 시 차단
        if (refundRepository.existsByOrderIdAndStatusIn(
                request.orderId(), List.of(RefundStatus.REQUESTED, RefundStatus.COMPLETED))) {
            throw new RefundException(ErrorCode.REFUND_ALREADY_EXISTS);
        }

        // 환불 금액 = 결제 금액 전액 (서버에서 자동 확정)
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
        eventPublisher.publishEvent(new RefundRequestedEvent(
                saved.getId(), order.getOrderUid(), order.getBuyerId()));
        return RefundResponse.from(saved);
    }

    /**
     * 7.4 환불 승인 (ADMIN)
     * - refunds.status → COMPLETED
     * - payments.status → REFUNDED
     * - orders.status → REFUNDED
     * 실제 금액 이체는 관리자가 별도 수동 처리 (PortOne 부분환불 미구현)
     */
    public RefundResponse approveRefund(Long refundId) {
        Refund refund = findRefund(refundId);
        validateRefundRequested(refund);

        Order order = findOrder(refund.getOrderId());
        Payment payment = paymentRepository.findById(refund.getPaymentId())
                .orElseThrow(() -> new PaymentException(ErrorCode.PAYMENT_NOT_FOUND));

        Settlement settlement = settlementRepository.findByOrderId(refund.getOrderId())
                .orElseThrow(() -> new SettlementException(ErrorCode.SETTLEMENT_NOT_FOUND));

        refund.approve();
        payment.refund();
        order.refund();
        settlement.refund();

        eventPublisher.publishEvent(new RefundApprovedEvent(
                refund.getId(), order.getOrderUid(), order.getBuyerId(), order.getSellerId()));
        return RefundResponse.from(refund);
    }

    /**
     * 7.5 환불 거절 (ADMIN)
     * - refunds.status → REJECTED
     * - orders.status는 기존 상태 유지
     */
    public RefundResponse rejectRefund(Long refundId, RejectRefundRequest request) {
        Refund refund = findRefund(refundId);
        validateRefundRequested(refund);

        Order order = findOrder(refund.getOrderId());
        refund.reject(request.rejectReason());
        eventPublisher.publishEvent(new RefundRejectedEvent(
                refund.getId(), order.getOrderUid(), order.getBuyerId(), request.rejectReason()));
        return RefundResponse.from(refund);
    }

    // ── 내부 헬퍼 ────────────────────────────────────────────────────

    private Refund findRefund(Long refundId) {
        // 비관적 락으로 승인·거절 동시 처리 시 상태 불일치 방지
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
