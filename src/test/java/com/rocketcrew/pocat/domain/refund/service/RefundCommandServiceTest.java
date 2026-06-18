package com.rocketcrew.pocat.domain.refund.service;

import com.rocketcrew.pocat.domain.order.entity.Order;
import com.rocketcrew.pocat.domain.order.enums.OrderStatus;
import com.rocketcrew.pocat.domain.order.repository.OrderRepository;
import com.rocketcrew.pocat.domain.payment.client.out.portone.PortOneClientService;
import com.rocketcrew.pocat.domain.payment.entity.Payment;
import com.rocketcrew.pocat.domain.payment.entity.PaymentStatus;
import com.rocketcrew.pocat.domain.payment.entity.PaymentType;
import com.rocketcrew.pocat.domain.payment.repository.PaymentRepository;
import com.rocketcrew.pocat.domain.refund.dto.request.CreateRefundRequest;
import com.rocketcrew.pocat.domain.refund.dto.request.RejectRefundRequest;
import com.rocketcrew.pocat.domain.refund.dto.response.RefundResponse;
import com.rocketcrew.pocat.domain.refund.entity.Refund;
import com.rocketcrew.pocat.domain.refund.entity.RefundStatus;
import com.rocketcrew.pocat.domain.refund.repository.RefundRepository;
import com.rocketcrew.pocat.domain.settlement.entity.Settlement;
import com.rocketcrew.pocat.domain.settlement.enums.SettlementStatus;
import com.rocketcrew.pocat.domain.settlement.repository.SettlementRepository;
import com.rocketcrew.pocat.global.exception.common.ErrorCode;
import com.rocketcrew.pocat.global.exception.domain.OrderException;
import com.rocketcrew.pocat.global.exception.domain.PaymentException;
import com.rocketcrew.pocat.global.exception.domain.RefundException;
import com.rocketcrew.pocat.global.exception.domain.SettlementException;
import com.rocketcrew.pocat.global.outbox.service.OutboxEventWriter;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.context.ApplicationEventPublisher;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import com.rocketcrew.pocat.domain.refund.event.RefundApprovedEvent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RefundCommandServiceTest {

    @InjectMocks
    private RefundCommandService refundCommandService;

    @Mock
    private RefundRepository refundRepository;

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private SettlementRepository settlementRepository;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @Mock
    private PortOneClientService portOneClientService;

    @Mock
    private OutboxEventWriter outboxEventWriter;

    private final Long buyerId = 3L;
    private final Long orderId = 100L;
    private final Long paymentId = 200L;
    private final Long refundId = 1L;

    private Order buildOrder(OrderStatus status) {
        Order order = Order.builder()
                .cardId(10L)
                .sellerId(2L)
                .buyerId(buyerId)
                .orderUid("ORD-001")
                .finalPrice(10000L)
                .status(status)
                .build();
        ReflectionTestUtils.setField(order, "id", orderId);
        return order;
    }

    private Payment buildPayment() {
        Payment payment = Payment.builder()
                .orderId(orderId)
                .paymentUid("PAY-001")
                .amount(10000L)
                .paymentType(PaymentType.PG_DIRECT)
                .status(PaymentStatus.COMPLETED)
                .paidAt(LocalDateTime.now())
                .build();
        ReflectionTestUtils.setField(payment, "id", paymentId);
        return payment;
    }

    private Refund buildRefund(RefundStatus status) {
        Refund refund = Refund.builder()
                .orderId(orderId)
                .paymentId(paymentId)
                .amount(10000L)
                .reason("단순 변심")
                .status(status)
                .build();
        ReflectionTestUtils.setField(refund, "id", refundId);
        return refund;
    }

    private Settlement buildSettlement() {
        Settlement settlement = Settlement.builder()
                .settlementUid("SET-001")
                .orderId(orderId)
                .sellerId(2L)
                .totalPrice(10000L)
                .platformFee(500L)
                .sellerAmount(9500L)
                .status(SettlementStatus.PENDING)
                .build();
        ReflectionTestUtils.setField(settlement, "id", 300L);
        return settlement;
    }

    @Nested
    @DisplayName("createRefund()")
    class CreateRefund {

        @Test
        @DisplayName("성공: 환불 요청 생성")
        void success() {
            // given
            CreateRefundRequest request = new CreateRefundRequest(orderId, "단순 변심");
            Order order = buildOrder(OrderStatus.PAYMENT_COMPLETED);
            Payment payment = buildPayment();
            Refund savedRefund = buildRefund(RefundStatus.REQUESTED);

            given(orderRepository.findByIdWithLock(orderId)).willReturn(Optional.of(order));
            given(refundRepository.existsByOrderIdAndStatusIn(eq(orderId), any())).willReturn(false);
            given(paymentRepository.findByOrderIdAndStatus(orderId, PaymentStatus.COMPLETED)).willReturn(Optional.of(payment));
            given(refundRepository.save(any(Refund.class))).willReturn(savedRefund);

            // when
            RefundResponse response = refundCommandService.createRefund(buyerId, request);

            // then
            assertThat(response).isNotNull();
            assertThat(response.orderId()).isEqualTo(orderId);
            assertThat(response.amount()).isEqualTo(10000L);
            assertThat(response.status()).isEqualTo(RefundStatus.REQUESTED);
            verify(refundRepository).save(any(Refund.class));
        }

        @Test
        @DisplayName("실패: 주문 없음")
        void fail_orderNotFound() {
            // given
            CreateRefundRequest request = new CreateRefundRequest(orderId, "사유");
            given(orderRepository.findByIdWithLock(orderId)).willReturn(Optional.empty());

            // when & then
            assertThatThrownBy(() -> refundCommandService.createRefund(buyerId, request))
                    .isInstanceOf(OrderException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.ORDER_NOT_FOUND);
        }

        @Test
        @DisplayName("실패: 요청자가 구매자가 아님")
        void fail_buyerMismatch() {
            // given
            CreateRefundRequest request = new CreateRefundRequest(orderId, "사유");
            Order order = buildOrder(OrderStatus.PAYMENT_COMPLETED);
            given(orderRepository.findByIdWithLock(orderId)).willReturn(Optional.of(order));

            Long wrongBuyerId = 999L;

            // when & then
            assertThatThrownBy(() -> refundCommandService.createRefund(wrongBuyerId, request))
                    .isInstanceOf(RefundException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.REFUND_BUYER_MISMATCH);
        }

        @Test
        @DisplayName("실패: 환불 불가 주문 상태 (PAYMENT_PENDING)")
        void fail_invalidOrderStatus() {
            // given
            CreateRefundRequest request = new CreateRefundRequest(orderId, "사유");
            Order order = buildOrder(OrderStatus.PAYMENT_PENDING);
            given(orderRepository.findByIdWithLock(orderId)).willReturn(Optional.of(order));

            // when & then
            assertThatThrownBy(() -> refundCommandService.createRefund(buyerId, request))
                    .isInstanceOf(RefundException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.REFUND_INVALID_ORDER_STATUS);
        }

        @Test
        @DisplayName("실패: 동일 주문에 환불 이미 존재")
        void fail_duplicateRefund() {
            // given
            CreateRefundRequest request = new CreateRefundRequest(orderId, "사유");
            Order order = buildOrder(OrderStatus.PAYMENT_COMPLETED);
            given(orderRepository.findByIdWithLock(orderId)).willReturn(Optional.of(order));
            given(refundRepository.existsByOrderIdAndStatusIn(eq(orderId), any())).willReturn(true);

            // when & then
            assertThatThrownBy(() -> refundCommandService.createRefund(buyerId, request))
                    .isInstanceOf(RefundException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.REFUND_ALREADY_EXISTS);
        }

        @Test
        @DisplayName("실패: 결제 없음")
        void fail_paymentNotFound() {
            // given
            CreateRefundRequest request = new CreateRefundRequest(orderId, "사유");
            Order order = buildOrder(OrderStatus.PAYMENT_COMPLETED);
            given(orderRepository.findByIdWithLock(orderId)).willReturn(Optional.of(order));
            given(refundRepository.existsByOrderIdAndStatusIn(
                    orderId, List.of(RefundStatus.REQUESTED, RefundStatus.COMPLETED))).willReturn(false);
            given(paymentRepository.findByOrderId(orderId)).willReturn(Optional.empty());

            // when & then
            assertThatThrownBy(() -> refundCommandService.createRefund(buyerId, request))
                    .isInstanceOf(PaymentException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.PAYMENT_NOT_FOUND);
        }
    }

    @Nested
    @DisplayName("approveRefund()")
    class ApproveRefund {

        @Test
        @DisplayName("성공: 환불 승인 — refund/payment/order/settlement 상태 업데이트")
        void success() {
            // given
            Refund refund = buildRefund(RefundStatus.REQUESTED);
            Order order = buildOrder(OrderStatus.PAYMENT_COMPLETED);
            Payment payment = buildPayment();
            Settlement settlement = buildSettlement();

            given(refundRepository.findByIdWithLock(refundId)).willReturn(Optional.of(refund));
            given(orderRepository.findById(orderId)).willReturn(Optional.of(order));
            given(paymentRepository.findById(paymentId)).willReturn(Optional.of(payment));
            given(settlementRepository.findByOrderId(orderId)).willReturn(Optional.of(settlement));

            // when
            RefundResponse response = refundCommandService.approveRefund(refundId);

            // then
            assertThat(refund.getStatus()).isEqualTo(RefundStatus.COMPLETED);
            assertThat(payment.getStatus()).isEqualTo(PaymentStatus.REFUNDED);
            assertThat(order.getStatus()).isEqualTo(OrderStatus.REFUNDED);
            assertThat(settlement.getStatus()).isEqualTo(SettlementStatus.REFUNDED);
            assertThat(response.status()).isEqualTo(RefundStatus.COMPLETED);
        }

        @Test
        @DisplayName("실패: 환불 없음")
        void fail_refundNotFound() {
            // given
            given(refundRepository.findByIdWithLock(refundId)).willReturn(Optional.empty());

            // when & then
            assertThatThrownBy(() -> refundCommandService.approveRefund(refundId))
                    .isInstanceOf(RefundException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.REFUND_NOT_FOUND);
        }

        @Test
        @DisplayName("실패: REQUESTED 상태가 아닌 환불")
        void fail_notRequested() {
            // given
            Refund refund = buildRefund(RefundStatus.COMPLETED);
            given(refundRepository.findByIdWithLock(refundId)).willReturn(Optional.of(refund));

            // when & then
            assertThatThrownBy(() -> refundCommandService.approveRefund(refundId))
                    .isInstanceOf(RefundException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.REFUND_NOT_REQUESTED);
        }
    }

    @Nested
    @DisplayName("rejectRefund()")
    class RejectRefund {

        @Test
        @DisplayName("성공: 환불 거절")
        void success() {
            // given
            Refund refund = buildRefund(RefundStatus.REQUESTED);
            Order order = buildOrder(OrderStatus.PAYMENT_COMPLETED);
            RejectRefundRequest request = new RejectRefundRequest("파손 없음");
            given(refundRepository.findByIdWithLock(refundId)).willReturn(Optional.of(refund));
            given(orderRepository.findById(orderId)).willReturn(Optional.of(order));

            // when
            RefundResponse response = refundCommandService.rejectRefund(refundId, request);

            // then
            assertThat(refund.getStatus()).isEqualTo(RefundStatus.REJECTED);
            assertThat(refund.getRejectReason()).isEqualTo("파손 없음");
            assertThat(response.status()).isEqualTo(RefundStatus.REJECTED);
            assertThat(response.rejectReason()).isEqualTo("파손 없음");
        }

        @Test
        @DisplayName("실패: 환불 없음")
        void fail_refundNotFound() {
            // given
            given(refundRepository.findByIdWithLock(refundId)).willReturn(Optional.empty());
            RejectRefundRequest request = new RejectRefundRequest("사유");

            // when & then
            assertThatThrownBy(() -> refundCommandService.rejectRefund(refundId, request))
                    .isInstanceOf(RefundException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.REFUND_NOT_FOUND);
        }

        @Test
        @DisplayName("실패: REQUESTED 상태가 아닌 환불")
        void fail_notRequested() {
            // given
            Refund refund = buildRefund(RefundStatus.REJECTED);
            given(refundRepository.findByIdWithLock(refundId)).willReturn(Optional.of(refund));
            RejectRefundRequest request = new RejectRefundRequest("사유");

            // when & then
            assertThatThrownBy(() -> refundCommandService.rejectRefund(refundId, request))
                    .isInstanceOf(RefundException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.REFUND_NOT_REQUESTED);
        }
    }

    @Nested
    @DisplayName("retryRefund()")
    class RetryRefund {

        @Test
        @DisplayName("성공: FAILED_RETRYABLE 상태에서 재시도 성공 — 상태 업데이트 및 이벤트 발행")
        void success() {
            // given
            Refund refund = buildRefund(RefundStatus.FAILED_RETRYABLE);
            Payment payment = buildPayment();
            Order order = buildOrder(OrderStatus.PAYMENT_COMPLETED);
            Settlement settlement = buildSettlement();

            given(refundRepository.findByIdWithLock(refundId)).willReturn(Optional.of(refund));
            given(paymentRepository.findByIdWithLock(paymentId)).willReturn(Optional.of(payment));
            given(orderRepository.findByIdWithLock(orderId)).willReturn(Optional.of(order));
            given(settlementRepository.findByOrderIdWithLock(orderId)).willReturn(Optional.of(settlement));

            // when
            refundCommandService.retryRefund(refundId);

            // then
            assertThat(refund.getStatus()).isEqualTo(RefundStatus.COMPLETED);
            assertThat(payment.getStatus()).isEqualTo(PaymentStatus.REFUNDED);
            assertThat(order.getStatus()).isEqualTo(OrderStatus.REFUNDED);
            assertThat(settlement.getStatus()).isEqualTo(SettlementStatus.REFUNDED);
            verify(outboxEventWriter).write(eq("refund"), eq("ORD-001"), any(RefundApprovedEvent.class));
            verify(eventPublisher).publishEvent(any(RefundApprovedEvent.class));
        }

        @Test
        @DisplayName("조기 종료: 재시도 대상 상태 아님 (COMPLETED)")
        void earlyReturn_invalidStatus() {
            // given
            Refund refund = buildRefund(RefundStatus.COMPLETED);
            given(refundRepository.findByIdWithLock(refundId)).willReturn(Optional.of(refund));

            // when
            refundCommandService.retryRefund(refundId);

            // then
            verify(portOneClientService, never()).cancelPayment(any(), any(), any());
        }

        @Test
        @DisplayName("조기 종료: 재시도 시간 미도래")
        void earlyReturn_notRetryDue() {
            // given
            Refund refund = buildRefund(RefundStatus.FAILED_RETRYABLE);
            ReflectionTestUtils.setField(refund, "nextRetryAt", LocalDateTime.now().plusHours(1));
            given(refundRepository.findByIdWithLock(refundId)).willReturn(Optional.of(refund));

            // when
            refundCommandService.retryRefund(refundId);

            // then
            verify(portOneClientService, never()).cancelPayment(any(), any(), any());
        }

        @Test
        @DisplayName("실패: PortOne 취소 실패 — FAILED_RETRYABLE 유지")
        void fail_portOneCancelFailed() {
            // given
            Refund refund = buildRefund(RefundStatus.FAILED_RETRYABLE);
            Payment payment = buildPayment();
            Order order = buildOrder(OrderStatus.PAYMENT_COMPLETED);
            Settlement settlement = buildSettlement();

            given(refundRepository.findByIdWithLock(refundId)).willReturn(Optional.of(refund));
            given(paymentRepository.findByIdWithLock(paymentId)).willReturn(Optional.of(payment));
            given(orderRepository.findByIdWithLock(orderId)).willReturn(Optional.of(order));
            given(settlementRepository.findByOrderIdWithLock(orderId)).willReturn(Optional.of(settlement));
            willThrow(new RuntimeException("PortOne 오류")).given(portOneClientService)
                    .cancelPayment(any(), any(), any());

            // when
            refundCommandService.retryRefund(refundId);

            // then
            assertThat(refund.getStatus()).isEqualTo(RefundStatus.FAILED_RETRYABLE);
            verify(eventPublisher, never()).publishEvent(any(RefundApprovedEvent.class));
        }
    }
}
