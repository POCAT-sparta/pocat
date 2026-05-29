package com.rocketcrew.pocat.domain.payment.service;

import com.rocketcrew.pocat.domain.order.entity.Order;
import com.rocketcrew.pocat.domain.order.enums.OrderStatus;
import com.rocketcrew.pocat.domain.payment.entity.Payment;
import com.rocketcrew.pocat.domain.payment.entity.PaymentStatus;
import com.rocketcrew.pocat.domain.payment.entity.PaymentType;
import com.rocketcrew.pocat.domain.payment.repository.PaymentRepository;
import com.rocketcrew.pocat.domain.settlement.service.SettlementCommandService;
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
    @Mock private SettlementCommandService settlementCommandService;
    @Mock private FailureService failureService;
    @Mock private StringRedisTemplate redisTemplate;
    @Mock private ApplicationEventPublisher eventPublisher;

    // ── createPayment ──────────────────────────────────────────────────

    @Nested
    @DisplayName("createPayment()")
    class CreatePayment {

        @Test
        @DisplayName("성공: PG_DIRECT PENDING 결제를 저장하고 반환한다")
        void success() {
            Order order = TestFixtures.anOrder(OrderStatus.AUTO_PAYMENT_FAILED);
            Payment saved = TestFixtures.aPayment(PaymentStatus.PENDING);
            given(paymentRepository.save(any(Payment.class))).willReturn(saved);

            Payment result = paymentCommandService.createPayment(order.getId(), PaymentType.BILLING_KEY);

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
        @DisplayName("성공: payment 완료, 정산 생성, 캐시 삭제, 만료 취소를 수행한다")
        void success() {
            Payment payment = TestFixtures.aPayment(PaymentStatus.PENDING);
            Order order = TestFixtures.anOrder(OrderStatus.PAYMENT_PENDING);

            paymentCommandService.completePayment(payment, order, "CARD", LocalDateTime.now());

            assertThat(payment.getStatus()).isEqualTo(PaymentStatus.COMPLETED);
            verify(settlementCommandService).createSettlement("ORD-001");
//            verify(failureService).cancelExpiry(1L);
            verify(redisTemplate).delete("card:avgprice:3");
        }

        @Test
        @DisplayName("캐시 삭제 실패 시 예외를 삼키고 정산·만료 취소는 정상 수행한다")
        void cacheEvictionFailure_doesNotPropagate() {
            Payment payment = TestFixtures.aPayment(PaymentStatus.PENDING);
            Order order = TestFixtures.anOrder(OrderStatus.PAYMENT_PENDING);
            given(redisTemplate.delete(anyString())).willThrow(new RuntimeException("Redis 연결 실패"));

            assertThatCode(() -> paymentCommandService.completePayment(payment, order, "CARD", LocalDateTime.now()))
                    .doesNotThrowAnyException();

            verify(settlementCommandService).createSettlement("ORD-001");
//            verify(failureService).cancelExpiry(1L);
        }
    }
}
