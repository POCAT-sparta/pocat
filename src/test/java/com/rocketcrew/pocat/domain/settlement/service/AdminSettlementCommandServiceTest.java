package com.rocketcrew.pocat.domain.settlement.service;

import com.rocketcrew.pocat.domain.settlement.dto.response.SettlementCompleteResponse;
import com.rocketcrew.pocat.domain.settlement.entity.Settlement;
import com.rocketcrew.pocat.domain.settlement.enums.SettlementStatus;
import com.rocketcrew.pocat.domain.settlement.event.SettlementCompletedEvent;
import com.rocketcrew.pocat.domain.settlement.repository.SettlementRepository;
import com.rocketcrew.pocat.global.exception.common.ErrorCode;
import com.rocketcrew.pocat.global.exception.domain.SettlementException;
import com.rocketcrew.pocat.global.outbox.service.OutboxEventWriter;
import org.springframework.context.ApplicationEventPublisher;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AdminSettlementCommandServiceTest {

    @InjectMocks
    private AdminSettlementCommandService adminSettlementCommandService;

    @Mock
    private SettlementRepository settlementRepository;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @Mock
    private OutboxEventWriter outboxEventWriter;

    private Settlement buildPendingSettlement() {
        Settlement settlement = Settlement.builder()
                .settlementUid("SET-001")
                .orderId(100L)
                .sellerId(2L)
                .totalPrice(10000L)
                .platformFee(500L)
                .sellerAmount(9500L)
                .status(SettlementStatus.PENDING)
                .build();
        ReflectionTestUtils.setField(settlement, "id", 1L);
        return settlement;
    }

    @Nested
    @DisplayName("completeSettlement()")
    class CompleteSettlement {

        @Test
        @DisplayName("성공: PENDING → COMPLETED 상태 전이 + outbox 선기록 후 이벤트 발행")
        void success() {
            // given
            Settlement settlement = buildPendingSettlement();
            given(settlementRepository.findWithLockBySettlementUid("SET-001"))
                    .willReturn(Optional.of(settlement));

            // when
            SettlementCompleteResponse response = adminSettlementCommandService.completeSettlement("SET-001");

            // then — 상태 전이
            assertThat(settlement.getStatus()).isEqualTo(SettlementStatus.COMPLETED);
            assertThat(settlement.getSettledAt()).isNotNull();
            assertThat(response.settlementUid()).isEqualTo("SET-001");
            assertThat(response.status()).isEqualTo(SettlementStatus.COMPLETED);

            // then — outbox 선기록 후 publish 순서 보장
            InOrder order = inOrder(outboxEventWriter, eventPublisher);
            order.verify(outboxEventWriter).write(eq("settlement"), eq("SET-001"), any(SettlementCompletedEvent.class));
            order.verify(eventPublisher).publishEvent(any(SettlementCompletedEvent.class));
        }

        @Test
        @DisplayName("실패: 정산 없음 → outbox·이벤트 미호출")
        void fail_settlementNotFound() {
            // given
            given(settlementRepository.findWithLockBySettlementUid("NOT-EXIST"))
                    .willReturn(Optional.empty());

            // when & then
            assertThatThrownBy(() -> adminSettlementCommandService.completeSettlement("NOT-EXIST"))
                    .isInstanceOf(SettlementException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.SETTLEMENT_NOT_FOUND);

            verify(outboxEventWriter, never()).write(any(), any(), any());
            verify(eventPublisher, never()).publishEvent(any());
        }

        @Test
        @DisplayName("실패: PENDING이 아닌 상태에서 complete() 호출 시 예외 → outbox·이벤트 미호출")
        void fail_notPendingStatus() {
            // given
            Settlement settlement = Settlement.builder()
                    .settlementUid("SET-002")
                    .orderId(101L)
                    .sellerId(2L)
                    .totalPrice(10000L)
                    .platformFee(500L)
                    .sellerAmount(9500L)
                    .status(SettlementStatus.COMPLETED)
                    .build();
            ReflectionTestUtils.setField(settlement, "id", 2L);

            given(settlementRepository.findWithLockBySettlementUid("SET-002"))
                    .willReturn(Optional.of(settlement));

            // when & then
            assertThatThrownBy(() -> adminSettlementCommandService.completeSettlement("SET-002"))
                    .isInstanceOf(SettlementException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.SETTLEMENT_CANNOT_COMPLETE);

            verify(outboxEventWriter, never()).write(any(), any(), any());
            verify(eventPublisher, never()).publishEvent(any());
        }
    }
}
