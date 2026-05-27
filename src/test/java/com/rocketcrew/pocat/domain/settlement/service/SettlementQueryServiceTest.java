package com.rocketcrew.pocat.domain.settlement.service;

import com.rocketcrew.pocat.domain.card.entity.Card;
import com.rocketcrew.pocat.domain.card.entity.enums.CardGrade;
import com.rocketcrew.pocat.domain.card.repository.CardRepository;
import com.rocketcrew.pocat.domain.order.entity.Order;
import com.rocketcrew.pocat.domain.order.repository.OrderRepository;
import com.rocketcrew.pocat.domain.settlement.dto.response.SettlementResponse;
import com.rocketcrew.pocat.domain.settlement.entity.Settlement;
import com.rocketcrew.pocat.domain.settlement.enums.SettlementStatus;
import com.rocketcrew.pocat.domain.settlement.repository.SettlementRepository;
import com.rocketcrew.pocat.global.exception.common.ErrorCode;
import com.rocketcrew.pocat.global.exception.domain.SettlementException;
import org.junit.jupiter.api.BeforeEach;
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
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SettlementQueryServiceTest {

    @InjectMocks
    private SettlementQueryService settlementQueryService;

    @Mock
    private SettlementRepository settlementRepository;

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private CardRepository cardRepository;

    private Settlement settlement;
    private Order order;
    private Card card;
    private final Long sellerId = 2L;

    @BeforeEach
    void setUp() {
        card = Card.builder()
                .userId(sellerId)
                .name("피카츄")
                .series(com.rocketcrew.pocat.support.TestFixtures.aSeries())
                .pokemonSet(com.rocketcrew.pocat.support.TestFixtures.aPokemonSet())
                .cardNumber("001")
                .rarity("Rare")
                .grade(CardGrade.PSA_10)
                .imageUrl("http://example.com/card.png")
                .build();
        ReflectionTestUtils.setField(card, "id", 10L);

        order = Order.builder()
                .cardId(10L)
                .sellerId(sellerId)
                .buyerId(3L)
                .orderUid("ORD-001")
                .finalPrice(10000L)
                .build();
        ReflectionTestUtils.setField(order, "id", 100L);

        settlement = Settlement.builder()
                .settlementUid("SET-001")
                .orderId(100L)
                .sellerId(sellerId)
                .totalPrice(10000L)
                .platformFee(500L)
                .sellerAmount(9500L)
                .status(SettlementStatus.PENDING)
                .build();
        ReflectionTestUtils.setField(settlement, "id", 1L);
    }

    @Nested
    @DisplayName("getSettlements()")
    class GetSettlements {

        @Test
        @DisplayName("성공: 페이지네이션 결과 반환")
        void success() {
            // given
            Pageable pageable = PageRequest.of(0, 20);
            Page<Settlement> settlementPage = new PageImpl<>(List.of(settlement), pageable, 1);

            given(settlementRepository.findBySellerId(sellerId, pageable)).willReturn(settlementPage);
            given(orderRepository.findAllById(List.of(100L))).willReturn(List.of(order));
            given(cardRepository.findAllById(List.of(10L))).willReturn(List.of(card));

            // when
            Page<SettlementResponse> result = settlementQueryService.getSettlements(sellerId, pageable);

            // then
            assertThat(result).isNotNull();
            assertThat(result.getTotalElements()).isEqualTo(1);
            assertThat(result.getContent().get(0).settlementUid()).isEqualTo("SET-001");
            assertThat(result.getContent().get(0).orderUid()).isEqualTo("ORD-001");
            assertThat(result.getContent().get(0).cardName()).isEqualTo("피카츄");
        }

        @Test
        @DisplayName("실패: 정산의 orderId에 해당하는 주문 없음")
        void fail_orderNotFoundInMap() {
            // given
            Pageable pageable = PageRequest.of(0, 20);
            Page<Settlement> settlementPage = new PageImpl<>(List.of(settlement), pageable, 1);

            given(settlementRepository.findBySellerId(sellerId, pageable)).willReturn(settlementPage);
            given(orderRepository.findAllById(List.of(100L))).willReturn(List.of()); // empty — order missing
            given(cardRepository.findAllById(anyList())).willReturn(List.of());

            // when & then
            assertThatThrownBy(() -> settlementQueryService.getSettlements(sellerId, pageable).getContent())
                    .isInstanceOf(SettlementException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.ORDER_NOT_FOUND);
        }

        @Test
        @DisplayName("실패: 주문의 cardId에 해당하는 카드 없음")
        void fail_cardNotFoundInMap() {
            // given
            Pageable pageable = PageRequest.of(0, 20);
            Page<Settlement> settlementPage = new PageImpl<>(List.of(settlement), pageable, 1);

            given(settlementRepository.findBySellerId(sellerId, pageable)).willReturn(settlementPage);
            given(orderRepository.findAllById(List.of(100L))).willReturn(List.of(order));
            given(cardRepository.findAllById(List.of(10L))).willReturn(List.of()); // empty — card missing

            // when & then
            assertThatThrownBy(() -> settlementQueryService.getSettlements(sellerId, pageable).getContent())
                    .isInstanceOf(SettlementException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.CARD_NOT_FOUND);
        }
    }

    @Nested
    @DisplayName("getOneSettlement()")
    class GetOneSettlement {

        @Test
        @DisplayName("성공: 단건 정산 조회")
        void success() {
            // given
            given(settlementRepository.findBySettlementUidAndSellerId("SET-001", sellerId))
                    .willReturn(Optional.of(settlement));
            given(orderRepository.findById(100L)).willReturn(Optional.of(order));
            given(cardRepository.findById(10L)).willReturn(Optional.of(card));

            // when
            SettlementResponse response = settlementQueryService.getOneSettlement(sellerId, "SET-001");

            // then
            assertThat(response).isNotNull();
            assertThat(response.settlementUid()).isEqualTo("SET-001");
            assertThat(response.totalPrice()).isEqualTo(10000L);
            assertThat(response.platformFee()).isEqualTo(500L);
            assertThat(response.sellerAmount()).isEqualTo(9500L);
        }

        @Test
        @DisplayName("실패: 정산 없음")
        void fail_settlementNotFound() {
            // given
            given(settlementRepository.findBySettlementUidAndSellerId("NOT-EXIST", sellerId))
                    .willReturn(Optional.empty());

            // when & then
            assertThatThrownBy(() -> settlementQueryService.getOneSettlement(sellerId, "NOT-EXIST"))
                    .isInstanceOf(SettlementException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.SETTLEMENT_NOT_FOUND);
        }

        @Test
        @DisplayName("실패: 주문 없음")
        void fail_orderNotFound() {
            // given
            given(settlementRepository.findBySettlementUidAndSellerId("SET-001", sellerId))
                    .willReturn(Optional.of(settlement));
            given(orderRepository.findById(100L)).willReturn(Optional.empty());

            // when & then
            assertThatThrownBy(() -> settlementQueryService.getOneSettlement(sellerId, "SET-001"))
                    .isInstanceOf(SettlementException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.ORDER_NOT_FOUND);
        }

        @Test
        @DisplayName("실패: 카드 없음")
        void fail_cardNotFound() {
            // given
            given(settlementRepository.findBySettlementUidAndSellerId("SET-001", sellerId))
                    .willReturn(Optional.of(settlement));
            given(orderRepository.findById(100L)).willReturn(Optional.of(order));
            given(cardRepository.findById(10L)).willReturn(Optional.empty());

            // when & then
            assertThatThrownBy(() -> settlementQueryService.getOneSettlement(sellerId, "SET-001"))
                    .isInstanceOf(SettlementException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.CARD_NOT_FOUND);
        }
    }
}
