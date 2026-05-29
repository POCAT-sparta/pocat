package com.rocketcrew.pocat.domain.settlement.service;

import com.rocketcrew.pocat.domain.order.entity.Order;
import com.rocketcrew.pocat.domain.order.repository.OrderRepository;
import com.rocketcrew.pocat.domain.settlement.entity.Settlement;
import com.rocketcrew.pocat.domain.settlement.enums.SettlementStatus;
import com.rocketcrew.pocat.domain.settlement.event.SettlementCreatedEvent;
import com.rocketcrew.pocat.domain.settlement.repository.SettlementRepository;
import com.rocketcrew.pocat.global.exception.common.ErrorCode;
import com.rocketcrew.pocat.global.exception.domain.SettlementException;
import com.rocketcrew.pocat.global.outbox.service.OutboxEventWriter;
import com.rocketcrew.pocat.global.util.PlatformFeePolicy;
import org.springframework.context.ApplicationEventPublisher;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SettlementCommandServiceTest {

    @InjectMocks
    private SettlementCommandService settlementCommandService;

    @Mock
    private SettlementRepository settlementRepository;

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @Mock
    private OutboxEventWriter outboxEventWriter;

    private Order buildOrder(Long id, Long finalPrice) {
        Order order = Order.builder()
                .cardId(10L)
                .sellerId(2L)
                .buyerId(3L)
                .orderUid("ORD-001")
                .finalPrice(finalPrice)
                .build();
        ReflectionTestUtils.setField(order, "id", id);
        return order;
    }

    @Nested
    @DisplayName("createSettlement()")
    class CreateSettlement {

        @Test
        @DisplayName("성공: 올바른 수수료 계산으로 정산 저장")
        void success_correctFeeCalculation() {
            // given
            long totalPrice = 10000L;
            Order order = buildOrder(1L, totalPrice);

            given(orderRepository.findByOrderUid("ORD-001")).willReturn(Optional.of(order));
            given(settlementRepository.existsByOrderId(1L)).willReturn(false);
            given(settlementRepository.saveAndFlush(any(Settlement.class))).willAnswer(inv -> inv.getArgument(0));

            // when
            settlementCommandService.createSettlement("ORD-001");

            // then
            ArgumentCaptor<Settlement> captor = ArgumentCaptor.forClass(Settlement.class);
            verify(settlementRepository).saveAndFlush(captor.capture());
            Settlement saved = captor.getValue();

            long expectedPlatformFee = BigDecimal.valueOf(totalPrice)
                    .multiply(BigDecimal.valueOf(PlatformFeePolicy.RATE))
                    .divide(BigDecimal.valueOf(100), 0, RoundingMode.HALF_UP)
                    .longValue();
            long expectedSellerAmount = totalPrice - expectedPlatformFee;

            assertThat(saved.getTotalPrice()).isEqualTo(totalPrice);
            assertThat(saved.getPlatformFee()).isEqualTo(expectedPlatformFee);
            assertThat(saved.getSellerAmount()).isEqualTo(expectedSellerAmount);
            assertThat(saved.getStatus()).isEqualTo(SettlementStatus.PENDING);
            assertThat(saved.getSellerId()).isEqualTo(2L);
            assertThat(saved.getOrderId()).isEqualTo(1L);

            // outbox 선기록 후 publish 순서 보장
            InOrder callOrder = inOrder(outboxEventWriter, eventPublisher);
            callOrder.verify(outboxEventWriter).write(eq("settlement"), any(String.class), any(SettlementCreatedEvent.class));
            callOrder.verify(eventPublisher).publishEvent(any(SettlementCreatedEvent.class));
        }

        @Test
        @DisplayName("성공(멱등): 정산이 이미 존재하면 저장하지 않고 종료")
        void success_idempotent_alreadyExists() {
            // given
            Order order = buildOrder(1L, 10000L);
            given(orderRepository.findByOrderUid("ORD-001")).willReturn(Optional.of(order));
            given(settlementRepository.existsByOrderId(1L)).willReturn(true);

            // when
            settlementCommandService.createSettlement("ORD-001");

            // then
            verify(settlementRepository, never()).saveAndFlush(any());
            verify(outboxEventWriter, never()).write(any(), any(), any());
            verify(eventPublisher, never()).publishEvent(any());
        }

        @Test
        @DisplayName("실패: 주문 없음 → outbox·이벤트 미호출")
        void fail_orderNotFound() {
            // given
            given(orderRepository.findByOrderUid("NOT-EXIST")).willReturn(Optional.empty());

            // when & then
            assertThatThrownBy(() -> settlementCommandService.createSettlement("NOT-EXIST"))
                    .isInstanceOf(SettlementException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.ORDER_NOT_FOUND);

            verify(outboxEventWriter, never()).write(any(), any(), any());
            verify(eventPublisher, never()).publishEvent(any());
        }

        @Test
        @DisplayName("성공(레이스 컨디션): DataIntegrityViolationException 발생 시 무시")
        void success_raceCondition_dataIntegrityViolation() {
            // given — 첫 번째 existsBy=false(진입 허용), saveAndFlush가 중복 오류, catch 블록 재확인 시 true
            Order order = buildOrder(1L, 10000L);
            given(orderRepository.findByOrderUid("ORD-001")).willReturn(Optional.of(order));
            given(settlementRepository.existsByOrderId(1L)).willReturn(false, true);
            given(settlementRepository.saveAndFlush(any(Settlement.class)))
                    .willThrow(new DataIntegrityViolationException("duplicate"));

            // when — should not throw
            settlementCommandService.createSettlement("ORD-001");

            // then — 저장 시도는 하되 outbox·이벤트는 발행하지 않고 조기 반환
            verify(settlementRepository).saveAndFlush(any(Settlement.class));
            verify(outboxEventWriter, never()).write(any(), any(), any());
            verify(eventPublisher, never()).publishEvent(any());
        }
    }
}
