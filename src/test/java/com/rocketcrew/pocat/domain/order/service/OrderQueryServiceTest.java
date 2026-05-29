package com.rocketcrew.pocat.domain.order.service;

import com.rocketcrew.pocat.domain.card.entity.Card;
import com.rocketcrew.pocat.domain.card.repository.CardRepository;
import com.rocketcrew.pocat.domain.order.dto.response.CardAveragePriceResponse;
import com.rocketcrew.pocat.domain.order.dto.response.OrderDetailResponse;
import com.rocketcrew.pocat.domain.order.dto.response.OrderResponse;
import com.rocketcrew.pocat.domain.order.entity.Order;
import com.rocketcrew.pocat.domain.order.enums.OrderStatus;
import com.rocketcrew.pocat.domain.order.repository.OrderRepository;
import com.rocketcrew.pocat.domain.user.entity.User;
import com.rocketcrew.pocat.domain.user.repository.UserRepository;
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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("OrderQueryService")
class OrderQueryServiceTest {

    @InjectMocks
    private OrderQueryService orderQueryService;

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private CardRepository cardRepository;

    @Mock
    private UserRepository userRepository;

    // ── getMyOrders ────────────────────────────────────────────────────

    @Nested
    @DisplayName("getMyOrders()")
    class GetMyOrders {

        @Test
        @DisplayName("성공: status 필터 없이 내 주문 목록을 반환한다")
        void success_noFilter() {
            Order order = TestFixtures.anOrder(OrderStatus.PAYMENT_COMPLETED);
            Card card = TestFixtures.aCard();
            Pageable pageable = PageRequest.of(0, 20);
            Page<Order> orderPage = new PageImpl<>(List.of(order), pageable, 1);

            given(orderRepository.findByBuyerId(1L, pageable)).willReturn(orderPage);
            given(cardRepository.findAllById(List.of(3L))).willReturn(List.of(card));

            Page<OrderResponse> result = orderQueryService.getMyOrders(1L, null, pageable);

            assertThat(result).isNotNull();
            assertThat(result.getContent()).hasSize(1);
            assertThat(result.getContent().get(0).cardName()).isEqualTo("피카츄");
        }

        @Test
        @DisplayName("성공: status 필터 적용 시 해당 상태의 주문만 반환한다")
        void success_withStatusFilter() {
            Order order = TestFixtures.anOrder(OrderStatus.PAYMENT_COMPLETED);
            Card card = TestFixtures.aCard();
            Pageable pageable = PageRequest.of(0, 20);
            Page<Order> orderPage = new PageImpl<>(List.of(order), pageable, 1);

            given(orderRepository.findByBuyerIdAndStatus(1L, OrderStatus.PAYMENT_COMPLETED, pageable))
                    .willReturn(orderPage);
            given(cardRepository.findAllById(List.of(3L))).willReturn(List.of(card));

            Page<OrderResponse> result = orderQueryService.getMyOrders(1L, OrderStatus.PAYMENT_COMPLETED, pageable);

            assertThat(result.getContent()).hasSize(1);
            assertThat(result.getContent().get(0).orderStatus()).isEqualTo("PAYMENT_COMPLETED");
        }
    }

    // ── getOneOrder ────────────────────────────────────────────────────

    @Nested
    @DisplayName("getOneOrder()")
    class GetOneOrder {

        @Test
        @DisplayName("성공: 본인 주문 상세를 반환한다")
        void success() {
            Order order = TestFixtures.anOrder(OrderStatus.PAYMENT_COMPLETED); // buyerId=1L, sellerId=2L, cardId=3L
            User buyer = TestFixtures.aUser();   // id=1L
            User seller = TestFixtures.anAdmin(); // id=2L
            Card card = TestFixtures.aCard();    // id=3L

            given(orderRepository.findByOrderUid("ORD-001")).willReturn(Optional.of(order));
            given(userRepository.findAllById(List.of(1L, 2L))).willReturn(List.of(buyer, seller));
            given(cardRepository.findById(3L)).willReturn(Optional.of(card));

            OrderDetailResponse response = orderQueryService.getOneOrder(1L, "ORD-001");

            assertThat(response).isNotNull();
            assertThat(response.orderUid()).isEqualTo("ORD-001");
            assertThat(response.buyer().nickname()).isEqualTo("구매자");
            assertThat(response.card().name()).isEqualTo("피카츄");
            verify(userRepository).findAllById(List.of(1L, 2L));
            verify(userRepository, never()).findById(any());
        }

        @Test
        @DisplayName("실패: 주문 없음 → ORDER_NOT_FOUND")
        void fail_orderNotFound() {
            given(orderRepository.findByOrderUid("UNKNOWN")).willReturn(Optional.empty());

            assertThatThrownBy(() -> orderQueryService.getOneOrder(1L, "UNKNOWN"))
                    .isInstanceOf(OrderException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.ORDER_NOT_FOUND);
        }

        @Test
        @DisplayName("실패: 구매자 아님 → ORDER_FORBIDDEN")
        void fail_forbidden() {
            Order order = TestFixtures.anOrder(OrderStatus.PAYMENT_COMPLETED); // buyerId=1L
            given(orderRepository.findByOrderUid("ORD-001")).willReturn(Optional.of(order));

            assertThatThrownBy(() -> orderQueryService.getOneOrder(99L, "ORD-001"))
                    .isInstanceOf(OrderException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.ORDER_FORBIDDEN);
        }

        @Test
        @DisplayName("실패: 구매자 누락 → USER_NOT_FOUND")
        void fail_buyerNotFound() {
            Order order = TestFixtures.anOrder(OrderStatus.PAYMENT_COMPLETED); // buyerId=1L, sellerId=2L
            User seller = TestFixtures.anAdmin(); // id=2L

            given(orderRepository.findByOrderUid("ORD-001")).willReturn(Optional.of(order));
            given(userRepository.findAllById(List.of(1L, 2L))).willReturn(List.of(seller)); // buyer 누락
            given(cardRepository.findById(3L)).willReturn(Optional.of(TestFixtures.aCard()));

            assertThatThrownBy(() -> orderQueryService.getOneOrder(1L, "ORD-001"))
                    .isInstanceOf(OrderException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.USER_NOT_FOUND);
        }

        @Test
        @DisplayName("실패: 판매자 누락 → USER_NOT_FOUND")
        void fail_sellerNotFound() {
            Order order = TestFixtures.anOrder(OrderStatus.PAYMENT_COMPLETED); // buyerId=1L, sellerId=2L
            User buyer = TestFixtures.aUser(); // id=1L

            given(orderRepository.findByOrderUid("ORD-001")).willReturn(Optional.of(order));
            given(userRepository.findAllById(List.of(1L, 2L))).willReturn(List.of(buyer)); // seller 누락
            given(cardRepository.findById(3L)).willReturn(Optional.of(TestFixtures.aCard()));

            assertThatThrownBy(() -> orderQueryService.getOneOrder(1L, "ORD-001"))
                    .isInstanceOf(OrderException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.USER_NOT_FOUND);
        }
    }

    // ── getAveragePriceByCard ──────────────────────────────────────────

    @Nested
    @DisplayName("getAveragePriceByCard()")
    class GetAveragePriceByCard {

        @Test
        @DisplayName("성공: 결과가 있을 때 평균가와 거래 수를 반환한다")
        void success_withResults() {
            Card card = TestFixtures.aCard(); // id=3L
            given(cardRepository.findById(3L)).willReturn(Optional.of(card));
            given(orderRepository.findAvgAndCountByCardId(eq(3L), eq(OrderStatus.ORDER_COMPLETED), any()))
                    .willReturn(new Object[]{12500.0, 5L});

            CardAveragePriceResponse response = orderQueryService.getAveragePriceByCard(3L);

            assertThat(response).isNotNull();
            assertThat(response.cardId()).isEqualTo(3L);
            assertThat(response.averagePrice()).isEqualTo(12500L);
            assertThat(response.transactionCount()).isEqualTo(5L);
        }

        @Test
        @DisplayName("성공: 거래 내역 없을 때 averagePrice=null, count=0 반환")
        void success_noResults() {
            Card card = TestFixtures.aCard();
            given(cardRepository.findById(3L)).willReturn(Optional.of(card));
            given(orderRepository.findAvgAndCountByCardId(eq(3L), eq(OrderStatus.ORDER_COMPLETED), any()))
                    .willReturn(new Object[]{null, 0L});

            CardAveragePriceResponse response = orderQueryService.getAveragePriceByCard(3L);

            assertThat(response.averagePrice()).isNull();
            assertThat(response.transactionCount()).isEqualTo(0L);
        }

        @Test
        @DisplayName("실패: 카드 없음 → CARD_NOT_FOUND")
        void fail_cardNotFound() {
            given(cardRepository.findById(99L)).willReturn(Optional.empty());

            assertThatThrownBy(() -> orderQueryService.getAveragePriceByCard(99L))
                    .isInstanceOf(OrderException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.CARD_NOT_FOUND);
        }
    }
}
