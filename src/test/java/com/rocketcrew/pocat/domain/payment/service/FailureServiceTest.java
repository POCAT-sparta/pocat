package com.rocketcrew.pocat.domain.payment.service;

import com.rocketcrew.pocat.domain.order.entity.Order;
import com.rocketcrew.pocat.domain.order.enums.OrderStatus;
import com.rocketcrew.pocat.domain.order.repository.OrderRepository;
import com.rocketcrew.pocat.global.exception.common.ErrorCode;
import com.rocketcrew.pocat.global.exception.domain.OrderException;
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
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doNothing;

import com.rocketcrew.pocat.domain.payment.enums.PaymentErrorReason;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("PaymentFailureService")
class FailureServiceTest {

    @InjectMocks
    private FailureService failureService;

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @Mock
    private OutboxEventWriter outboxEventWriter;

    // ── markFailed ─────────────────────────────────────────────────────

    @Nested
    @DisplayName("markFailed()")
    class MarkFailed {

        @Test
        @DisplayName("성공: order.failPayment() 가 호출되어 PAYMENT_FAILED 로 전이한다")
        void success() {
            Order order = TestFixtures.anOrder(OrderStatus.PAYMENT_PENDING);  // id=1L

            given(orderRepository.findById(1L)).willReturn(Optional.of(order));
            doNothing().when(outboxEventWriter).write(anyString(), anyString(), any());

            failureService.markFailed("",1L, PaymentErrorReason.PAYMENT_EXPIRED);

            assertThat(order.getStatus()).isEqualTo(OrderStatus.AUTO_PAYMENT_FAILED);
        }

        @Test
        @DisplayName("실패: orderId 에 해당하는 주문이 없음 → ORDER_NOT_FOUND")
        void fail_orderNotFound() {
            given(orderRepository.findById(99L)).willReturn(Optional.empty());

            assertThatThrownBy(() -> failureService.markFailed("",99L, PaymentErrorReason.PAYMENT_EXPIRED))
                    .isInstanceOf(OrderException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.ORDER_NOT_FOUND);
        }
    }
}
