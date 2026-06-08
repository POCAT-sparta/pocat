package com.rocketcrew.pocat.domain.payment.service;

import com.rocketcrew.pocat.domain.order.entity.Order;
import com.rocketcrew.pocat.domain.order.enums.OrderStatus;
import com.rocketcrew.pocat.domain.order.enums.OrderType;
import com.rocketcrew.pocat.domain.order.service.OrderQueryService;
import com.rocketcrew.pocat.domain.payment.entity.Payment;
import com.rocketcrew.pocat.domain.payment.entity.PaymentStatus;
import com.rocketcrew.pocat.global.exception.common.ErrorCode;
import com.rocketcrew.pocat.global.exception.domain.OrderException;
import com.rocketcrew.pocat.global.exception.domain.PaymentException;
import com.rocketcrew.pocat.global.outbox.service.OutboxEventWriter;
import com.rocketcrew.pocat.support.TestFixtures;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.rocketcrew.pocat.domain.payment.enums.PaymentErrorReason;
import com.rocketcrew.pocat.global.metrics.PaymentMetrics;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("PaymentFailureService")
class FailureServiceTest {

    @InjectMocks
    private FailureService failureService;

    @Mock
    private OrderQueryService orderQueryService;

    @Mock
    private PaymentQueryService paymentQueryService;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @Mock
    private OutboxEventWriter outboxEventWriter;

    @Mock
    private PaymentMetrics paymentMetrics;

    // ── markFailed ─────────────────────────────────────────────────────

    @Nested
    @DisplayName("markFailed()")
    class MarkFailed {

        @Test
        @DisplayName("성공: 기한 초과 + PAYMENT_PENDING → order.failPayment() 호출")
        void success() {
            Payment payment = TestFixtures.aPayment(PaymentStatus.PENDING);
            Order order = TestFixtures.anOrder(OrderStatus.PAYMENT_PENDING);
            // 결제 기한을 과거로 설정해 failPayment() 분기 진입
            ReflectionTestUtils.setField(order, "paymentDeadline", LocalDateTime.now().minusHours(1));

            given(paymentQueryService.findPaymentByUidWithLock("PAY-001")).willReturn(payment);
            given(orderQueryService.findByOrderIdWithLock(1L)).willReturn(order);
            doNothing().when(outboxEventWriter).write(anyString(), anyString(), any());

            failureService.markFailed("PAY-001", 1L, PaymentErrorReason.PAYMENT_EXPIRED);

            assertThat(order.getStatus()).isEqualTo(OrderStatus.AUTO_PAYMENT_FAILED);
        }

        @Test
        @DisplayName("성공: 기한 미초과 → order 상태 변경 없이 payment만 FAILED")
        void success_deadlineNotExpired() {
            Payment payment = TestFixtures.aPayment(PaymentStatus.PENDING);
            Order order = TestFixtures.anOrder(OrderStatus.PAYMENT_PENDING);

            given(paymentQueryService.findPaymentByUidWithLock("PAY-001")).willReturn(payment);
            given(orderQueryService.findByOrderIdWithLock(1L)).willReturn(order);

            failureService.markFailed("PAY-001", 1L, PaymentErrorReason.PAYMENT_EXPIRED);

            assertThat(payment.getStatus()).isEqualTo(PaymentStatus.FAILED);
            assertThat(order.getStatus()).isEqualTo(OrderStatus.PAYMENT_PENDING);
        }

        @Test
        @DisplayName("실패: orderId 에 해당하는 주문이 없음 → ORDER_NOT_FOUND")
        void fail_orderNotFound() {
            Payment payment = TestFixtures.aPayment(PaymentStatus.PENDING);
            ReflectionTestUtils.setField(payment, "orderId", 99L);
            given(paymentQueryService.findPaymentByUidWithLock("PAY-001")).willReturn(payment);
            given(orderQueryService.findByOrderIdWithLock(99L))
                    .willThrow(new OrderException(ErrorCode.ORDER_NOT_FOUND));

            assertThatThrownBy(() -> failureService.markFailed("PAY-001", 99L, PaymentErrorReason.PAYMENT_EXPIRED))
                    .isInstanceOf(OrderException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.ORDER_NOT_FOUND);
        }

        @Test
        @DisplayName("실패: payment의 orderId와 요청 orderId가 다르면 주문을 실패 처리하지 않는다")
        void fail_orderMismatch() {
            Payment payment = TestFixtures.aPayment(PaymentStatus.PENDING);
            ReflectionTestUtils.setField(payment, "orderId", 99L);
            given(paymentQueryService.findPaymentByUidWithLock("PAY-001")).willReturn(payment);

            assertThatThrownBy(() -> failureService.markFailed("PAY-001", 1L, PaymentErrorReason.PAYMENT_EXPIRED))
                    .isInstanceOf(PaymentException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.PAYMENT_ORDER_MISMATCH);

            verify(orderQueryService, never()).findByOrderIdWithLock(any());
        }
    }

    @Nested
    @DisplayName("handleAutoPaymentFailure()")
    class HandleAutoPaymentFailure {

        @Test
        @DisplayName("즉시구매 자동결제 실패는 주문을 취소하고 직접결제 이벤트를 발행하지 않는다")
        void buyoutFailure_cancelsOrderWithoutAutoPaymentFailedEvent() {
            Payment payment = TestFixtures.aBillingKeyPayment(PaymentStatus.PENDING);
            Order order = TestFixtures.anBuyoutOrder(OrderStatus.PAYMENT_PENDING);
            given(paymentQueryService.findPaymentByIdWithLock(1L)).willReturn(payment);
            given(orderQueryService.findByOrderIdWithLock(1L)).willReturn(order);

            failureService.handleAutoPaymentFailure(1L, 1L);

            assertThat(payment.getStatus()).isEqualTo(PaymentStatus.FAILED);
            assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
            verify(outboxEventWriter, never()).write(anyString(), anyString(), any());
            verify(eventPublisher, never()).publishEvent(any(Object.class));
        }

        @Test
        @DisplayName("경매 자동결제 실패는 주문을 자동결제 실패 상태로 바꾸고 이벤트를 발행한다")
        void auctionFailure_failsOrderAndPublishesAutoPaymentFailedEvent() {
            Payment payment = TestFixtures.aBillingKeyPayment(PaymentStatus.PENDING);
            Order order = TestFixtures.anOrder(OrderStatus.PAYMENT_PENDING);
            given(paymentQueryService.findPaymentByIdWithLock(1L)).willReturn(payment);
            given(orderQueryService.findByOrderIdWithLock(1L)).willReturn(order);
            doNothing().when(outboxEventWriter).write(anyString(), anyString(), any());

            failureService.handleAutoPaymentFailure(1L, 1L);

            assertThat(payment.getStatus()).isEqualTo(PaymentStatus.FAILED);
            assertThat(order.getStatus()).isEqualTo(OrderStatus.AUTO_PAYMENT_FAILED);
            assertThat(order.getOrderType()).isEqualTo(OrderType.AUCTION);
            verify(outboxEventWriter).write(anyString(), eq("ORD-001"), any());
            verify(eventPublisher).publishEvent(any(Object.class));
        }

        @Test
        @DisplayName("실패: payment의 orderId와 요청 orderId가 다르면 자동결제 실패 처리하지 않는다")
        void fail_orderMismatch() {
            Payment payment = TestFixtures.aBillingKeyPayment(PaymentStatus.PENDING);
            ReflectionTestUtils.setField(payment, "orderId", 99L);
            given(paymentQueryService.findPaymentByIdWithLock(1L)).willReturn(payment);

            assertThatThrownBy(() -> failureService.handleAutoPaymentFailure(1L, 1L))
                    .isInstanceOf(PaymentException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.PAYMENT_ORDER_MISMATCH);

            verify(orderQueryService, never()).findByOrderIdWithLock(any());
            verify(outboxEventWriter, never()).write(anyString(), anyString(), any());
            verify(eventPublisher, never()).publishEvent(any(Object.class));
        }
    }
}
