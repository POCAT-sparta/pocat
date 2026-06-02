package com.rocketcrew.pocat.domain.payment.service;

import com.rocketcrew.pocat.domain.order.entity.Order;
import com.rocketcrew.pocat.domain.order.enums.OrderStatus;
import com.rocketcrew.pocat.domain.order.service.OrderQueryService;
import com.rocketcrew.pocat.domain.order.service.SetExpireService;
import com.rocketcrew.pocat.domain.payment.entity.Payment;
import com.rocketcrew.pocat.domain.payment.entity.PaymentStatus;
import com.rocketcrew.pocat.domain.payment.entity.PaymentType;
import com.rocketcrew.pocat.domain.payment.repository.PaymentRepository;
import com.rocketcrew.pocat.global.exception.common.ErrorCode;
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
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
@DisplayName("PaymentCommandService")
class PaymentCommandServiceTest {

    @InjectMocks
    private PaymentCommandService paymentCommandService;

    @Mock private PaymentRepository paymentRepository;
    @Mock private PaymentQueryService paymentQueryService;
    @Mock private OrderQueryService orderQueryService;
    @Mock private SetExpireService setExpireService;
    @Mock private StringRedisTemplate redisTemplate;
    @Mock private ApplicationEventPublisher eventPublisher;
    @Mock private OutboxEventWriter outboxEventWriter;

    @Nested
    @DisplayName("createPayment()")
    class CreatePayment {

        @Test
        @DisplayName("성공: PG_DIRECT PENDING 결제를 저장하고 반환한다")
        void success() {
            Order order = TestFixtures.anOrder(OrderStatus.AUTO_PAYMENT_FAILED);
            Payment saved = TestFixtures.aPayment(PaymentStatus.PENDING);
            given(orderQueryService.findByOrderid(1L)).willReturn(order);
            given(paymentRepository.save(any(Payment.class))).willReturn(saved);

            Payment result = paymentCommandService.createPayment(order.getId(), PaymentType.PG_DIRECT);

            assertThat(result.getStatus()).isEqualTo(PaymentStatus.PENDING);
            assertThat(result.getAmount()).isEqualTo(10000L);
            verify(paymentRepository).save(any(Payment.class));
        }
    }

    @Nested
    @DisplayName("createBillingKeyPaymentIfAbsent()")
    class CreateBillingKeyPaymentIfAbsent {

        @Test
        @DisplayName("성공: 기존 자동결제가 없으면 BILLING_KEY 결제를 생성한다")
        void success_createNew() {
            Order order = TestFixtures.anOrder(OrderStatus.PAYMENT_PENDING);
            Payment saved = TestFixtures.aBillingKeyPayment(PaymentStatus.PENDING);
            given(orderQueryService.findByOrderIdWithLock(1L)).willReturn(order);
            given(paymentRepository.findByOrderIdAndPaymentType(1L, PaymentType.BILLING_KEY)).willReturn(Optional.empty());
            given(paymentRepository.save(any(Payment.class))).willReturn(saved);

            PaymentCommandService.BillingKeyPayment result = paymentCommandService.createBillingKeyPaymentIfAbsent(1L);

            assertThat(result.created()).isTrue();
            assertThat(result.payment()).isSameAs(saved);
            verify(paymentRepository).save(any(Payment.class));
        }

        @Test
        @DisplayName("멱등: 기존 자동결제가 있으면 새 결제를 생성하지 않는다")
        void idempotent_returnExisting() {
            Order order = TestFixtures.anOrder(OrderStatus.PAYMENT_PENDING);
            Payment existing = TestFixtures.aBillingKeyPayment(PaymentStatus.PENDING);
            given(orderQueryService.findByOrderIdWithLock(1L)).willReturn(order);
            given(paymentRepository.findByOrderIdAndPaymentType(1L, PaymentType.BILLING_KEY)).willReturn(Optional.of(existing));

            PaymentCommandService.BillingKeyPayment result = paymentCommandService.createBillingKeyPaymentIfAbsent(1L);

            assertThat(result.created()).isFalse();
            assertThat(result.payment()).isSameAs(existing);
        }
    }

    @Nested
    @DisplayName("completePayment()")
    class CompletePayment {

        @Test
        @DisplayName("성공: payment 완료, 캐시 삭제, 완료 이벤트 발행")
        void success() {
            Payment payment = TestFixtures.aPayment(PaymentStatus.PENDING);
            Order order = TestFixtures.anOrder(OrderStatus.PAYMENT_PENDING);
            given(paymentQueryService.findPaymentByIdWithLock(1L)).willReturn(payment);
            given(orderQueryService.findByOrderIdWithLock(1L)).willReturn(order);

            paymentCommandService.completePayment(1L, 1L, "CARD", LocalDateTime.now());

            assertThat(payment.getStatus()).isEqualTo(PaymentStatus.COMPLETED);
            verify(redisTemplate).delete("card:avgprice:3");
        }

        @Test
        @DisplayName("캐시 삭제 실패 시 예외를 삼키고 완료 처리는 정상 수행한다")
        void cacheEvictionFailure_doesNotPropagate() {
            Payment payment = TestFixtures.aPayment(PaymentStatus.PENDING);
            Order order = TestFixtures.anOrder(OrderStatus.PAYMENT_PENDING);
            given(paymentQueryService.findPaymentByIdWithLock(1L)).willReturn(payment);
            given(orderQueryService.findByOrderIdWithLock(1L)).willReturn(order);
            given(redisTemplate.delete(anyString())).willThrow(new RuntimeException("Redis 연결 실패"));

            assertThatCode(() -> paymentCommandService.completePayment(1L, 1L, "CARD", LocalDateTime.now()))
                    .doesNotThrowAnyException();

            assertThat(payment.getStatus()).isEqualTo(PaymentStatus.COMPLETED);
        }

        @Test
        @DisplayName("실패: payment의 orderId와 요청 orderId가 다르면 주문을 완료하지 않는다")
        void fail_orderMismatch() {
            Payment payment = TestFixtures.aPayment(PaymentStatus.PENDING);
            ReflectionTestUtils.setField(payment, "orderId", 99L);
            given(paymentQueryService.findPaymentByIdWithLock(1L)).willReturn(payment);

            assertThatThrownBy(() -> paymentCommandService.completePayment(1L, 1L, "CARD", LocalDateTime.now()))
                    .isInstanceOf(PaymentException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.PAYMENT_ORDER_MISMATCH);

            verify(orderQueryService, never()).findByOrderIdWithLock(any());
        }
    }

    @Nested
    @DisplayName("handleCancel()")
    class HandleCancel {

        @Test
        @DisplayName("실패: payment의 orderId와 요청 orderId가 다르면 주문을 실패 처리하지 않는다")
        void fail_orderMismatch() {
            Payment payment = TestFixtures.aPayment(PaymentStatus.PENDING);
            ReflectionTestUtils.setField(payment, "orderId", 99L);
            given(paymentQueryService.findPaymentByUidWithLock("PAY-001")).willReturn(payment);

            assertThatThrownBy(() -> paymentCommandService.handleCancel("PAY-001", 1L))
                    .isInstanceOf(PaymentException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.PAYMENT_ORDER_MISMATCH);

            verify(orderQueryService, never()).findByOrderIdWithLock(any());
        }
    }

    @Nested
    @DisplayName("markBillingKeyRequested()")
    class MarkBillingKeyRequested {

        @Test
        @DisplayName("성공: PENDING BILLING_KEY 결제에 PG 요청 marker를 기록한다")
        void success() {
            Payment payment = TestFixtures.aBillingKeyPayment(PaymentStatus.PENDING);
            given(paymentQueryService.findPaymentByIdWithLock(1L)).willReturn(payment);

            boolean result = paymentCommandService.markBillingKeyRequested(1L);

            assertThat(result).isTrue();
            assertThat(payment.getBillingKeyRequestedAt()).isNotNull();
        }

        @Test
        @DisplayName("멱등: 이미 marker가 있으면 다시 기록하지 않는다")
        void idempotent_alreadyRequested() {
            Payment payment = TestFixtures.aRequestedBillingKeyPayment(PaymentStatus.PENDING);
            given(paymentQueryService.findPaymentByIdWithLock(1L)).willReturn(payment);

            boolean result = paymentCommandService.markBillingKeyRequested(1L);

            assertThat(result).isFalse();
        }
    }
}
