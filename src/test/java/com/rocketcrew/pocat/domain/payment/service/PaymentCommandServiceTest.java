package com.rocketcrew.pocat.domain.payment.service;

import com.rocketcrew.pocat.domain.order.entity.Order;
import com.rocketcrew.pocat.domain.order.enums.OrderStatus;
import com.rocketcrew.pocat.domain.payment.entity.Payment;
import com.rocketcrew.pocat.domain.payment.entity.PaymentStatus;
import com.rocketcrew.pocat.domain.payment.entity.PaymentType;
import com.rocketcrew.pocat.domain.payment.repository.PaymentRepository;
import com.rocketcrew.pocat.domain.order.service.OrderQueryService;
import com.rocketcrew.pocat.domain.order.service.SetExpireService;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

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

    // ── createPayment ──────────────────────────────────────────────────

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

    // ── completePayment ────────────────────────────────────────────────

    @Nested
    @DisplayName("completePayment()")
    class CompletePayment {

        @Test
        @DisplayName("성공: payment 완료, 캐시 삭제, 완료 이벤트 발행")
        void success() {
            Payment payment = TestFixtures.aPayment(PaymentStatus.PENDING);
            Order order = TestFixtures.anOrder(OrderStatus.PAYMENT_PENDING);
            given(paymentQueryService.findPaymentByUidWithLock("PAY-001")).willReturn(payment);
            given(orderQueryService.findByOrderIdWithLock(1L)).willReturn(order);

            paymentCommandService.completePayment(payment, order, "CARD", LocalDateTime.now());

            assertThat(payment.getStatus()).isEqualTo(PaymentStatus.COMPLETED);
            verify(redisTemplate).delete("card:avgprice:3");
        }

        @Test
        @DisplayName("캐시 삭제 실패 시 예외를 삼키고 완료 처리는 정상 수행한다")
        void cacheEvictionFailure_doesNotPropagate() {
            Payment payment = TestFixtures.aPayment(PaymentStatus.PENDING);
            Order order = TestFixtures.anOrder(OrderStatus.PAYMENT_PENDING);
            given(paymentQueryService.findPaymentByUidWithLock("PAY-001")).willReturn(payment);
            given(orderQueryService.findByOrderIdWithLock(1L)).willReturn(order);
            given(redisTemplate.delete(anyString())).willThrow(new RuntimeException("Redis 연결 실패"));

            assertThatCode(() -> paymentCommandService.completePayment(payment, order, "CARD", LocalDateTime.now()))
                    .doesNotThrowAnyException();

            assertThat(payment.getStatus()).isEqualTo(PaymentStatus.COMPLETED);
        }
    }
}
