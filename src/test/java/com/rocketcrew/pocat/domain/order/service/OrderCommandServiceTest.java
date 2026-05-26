package com.rocketcrew.pocat.domain.order.service;

import com.rocketcrew.pocat.domain.card.entity.Card;
import com.rocketcrew.pocat.domain.card.repository.CardRepository;
import com.rocketcrew.pocat.domain.order.dto.response.OrderResponse;
import com.rocketcrew.pocat.domain.order.entity.Order;
import com.rocketcrew.pocat.domain.order.enums.DeliveryStatus;
import com.rocketcrew.pocat.domain.order.enums.OrderStatus;
import com.rocketcrew.pocat.domain.order.repository.OrderRepository;
import com.rocketcrew.pocat.global.exception.common.ErrorCode;
import com.rocketcrew.pocat.global.exception.domain.OrderException;
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
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("OrderCommandService")
class OrderCommandServiceTest {

    @InjectMocks
    private OrderCommandService orderCommandService;

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private CardRepository cardRepository;

    // ── cancelOrder ────────────────────────────────────────────────────

    @Nested
    @DisplayName("cancelOrder()")
    class CancelOrder {

        @Test
        @DisplayName("성공: PAYMENT_COMPLETED 주문을 취소한다")
        void success() {
            Order order = TestFixtures.anOrder(OrderStatus.PAYMENT_COMPLETED);
            Card card = TestFixtures.aCard();

            given(orderRepository.findByOrderUid("ORD-001")).willReturn(Optional.of(order));
            given(cardRepository.findById(3L)).willReturn(Optional.of(card));

            OrderResponse response = orderCommandService.cancelOrder(1L, "ORD-001", "단순 변심");

            assertThat(response).isNotNull();
            assertThat(response.orderStatus()).isEqualTo(OrderStatus.CANCELLED.name());
        }

        @Test
        @DisplayName("실패: 주문 없음 → ORDER_NOT_FOUND")
        void fail_orderNotFound() {
            given(orderRepository.findByOrderUid("UNKNOWN")).willReturn(Optional.empty());

            assertThatThrownBy(() -> orderCommandService.cancelOrder(1L, "UNKNOWN", "사유"))
                    .isInstanceOf(OrderException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.ORDER_NOT_FOUND);
        }

        @Test
        @DisplayName("실패: 구매자 아님 → ORDER_FORBIDDEN")
        void fail_forbidden() {
            Order order = TestFixtures.anOrder(OrderStatus.PAYMENT_COMPLETED); // buyerId=1L
            given(orderRepository.findByOrderUid("ORD-001")).willReturn(Optional.of(order));

            assertThatThrownBy(() -> orderCommandService.cancelOrder(99L, "ORD-001", "사유"))
                    .isInstanceOf(OrderException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.ORDER_FORBIDDEN);
        }

        @Test
        @DisplayName("실패: 이미 취소된 주문 → ORDER_ALREADY_CANCELLED")
        void fail_alreadyCancelled() {
            Order order = TestFixtures.anOrder(OrderStatus.CANCELLED);
            given(orderRepository.findByOrderUid("ORD-001")).willReturn(Optional.of(order));

            assertThatThrownBy(() -> orderCommandService.cancelOrder(1L, "ORD-001", "사유"))
                    .isInstanceOf(OrderException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.ORDER_ALREADY_CANCELLED);
        }

        @Test
        @DisplayName("실패: REFUNDED 상태 주문은 취소 불가 → ORDER_CANNOT_CANCEL")
        void fail_refundedCannotCancel() {
            Order order = TestFixtures.anOrder(OrderStatus.REFUNDED);
            given(orderRepository.findByOrderUid("ORD-001")).willReturn(Optional.of(order));

            assertThatThrownBy(() -> orderCommandService.cancelOrder(1L, "ORD-001", "사유"))
                    .isInstanceOf(OrderException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.ORDER_CANNOT_CANCEL);
        }

        @Test
        @DisplayName("실패: 배송 중(SHIPPING) 주문은 취소 불가 → ORDER_CANNOT_CANCEL")
        void fail_shippingCannotCancel() {
            Order order = TestFixtures.aShippingOrder(); // deliveryStatus=SHIPPING
            given(orderRepository.findByOrderUid("ORD-001")).willReturn(Optional.of(order));

            assertThatThrownBy(() -> orderCommandService.cancelOrder(1L, "ORD-001", "사유"))
                    .isInstanceOf(OrderException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.ORDER_CANNOT_CANCEL);
        }
    }
}
