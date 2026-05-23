package com.rocketcrew.pocat.domain.payment.service;

import com.rocketcrew.pocat.domain.order.entity.Order;
import com.rocketcrew.pocat.domain.order.enums.OrderStatus;
import com.rocketcrew.pocat.domain.order.repository.OrderRepository;
import com.rocketcrew.pocat.domain.payment.entity.Payment;
import com.rocketcrew.pocat.domain.payment.entity.PaymentStatus;
import com.rocketcrew.pocat.domain.payment.repository.PaymentRepository;
import com.rocketcrew.pocat.global.exception.common.ErrorCode;
import com.rocketcrew.pocat.global.exception.domain.PaymentException;
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

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("PaymentFailureService")
class PaymentFailureServiceTest {

    @InjectMocks
    private PaymentFailureService paymentFailureService;

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private OrderRepository orderRepository;

    // ── markFailed ─────────────────────────────────────────────────────

    @Nested
    @DisplayName("markFailed()")
    class MarkFailed {

        @Test
        @DisplayName("성공: payment.fail() 및 order.failPayment() 가 호출되어 FAILED/PAYMENT_FAILED 로 전이한다")
        void success() {
            Payment payment = TestFixtures.aPayment(PaymentStatus.PENDING); // id=1L, orderId=1L
            Order order = TestFixtures.anOrder(OrderStatus.PAYMENT_PENDING);  // id=1L

            given(paymentRepository.findById(1L)).willReturn(Optional.of(payment));
            given(orderRepository.findById(1L)).willReturn(Optional.of(order));

            paymentFailureService.markFailed(1L);

            assertThat(payment.getStatus()).isEqualTo(PaymentStatus.FAILED);
            assertThat(order.getStatus()).isEqualTo(OrderStatus.PAYMENT_FAILED);
        }

        @Test
        @DisplayName("실패: paymentId 에 해당하는 결제가 없음 → PAYMENT_NOT_FOUND")
        void fail_paymentNotFound() {
            given(paymentRepository.findById(99L)).willReturn(Optional.empty());

            assertThatThrownBy(() -> paymentFailureService.markFailed(99L))
                    .isInstanceOf(PaymentException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.PAYMENT_NOT_FOUND);
        }

        @Test
        @DisplayName("실패: payment 의 orderId 에 해당하는 주문이 없음 → ORDER_NOT_FOUND")
        void fail_orderNotFound() {
            Payment payment = TestFixtures.aPayment(PaymentStatus.PENDING); // orderId=1L

            given(paymentRepository.findById(1L)).willReturn(Optional.of(payment));
            given(orderRepository.findById(1L)).willReturn(Optional.empty());

            // PaymentFailureService 는 주문 미발견 시 PaymentException(ORDER_NOT_FOUND) 를 던진다
            assertThatThrownBy(() -> paymentFailureService.markFailed(1L))
                    .isInstanceOf(PaymentException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.ORDER_NOT_FOUND);
        }
    }
}
