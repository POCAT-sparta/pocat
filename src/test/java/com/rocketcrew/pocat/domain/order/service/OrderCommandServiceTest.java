package com.rocketcrew.pocat.domain.order.service;

import com.rocketcrew.pocat.domain.auction.repository.AuctionRepository;
import com.rocketcrew.pocat.domain.bid.entity.AuctionBid;
import com.rocketcrew.pocat.domain.bid.enums.BidStatus;
import com.rocketcrew.pocat.domain.bid.repository.AuctionBidRepository;
import com.rocketcrew.pocat.domain.card.entity.Card;
import com.rocketcrew.pocat.domain.card.repository.CardRepository;
import com.rocketcrew.pocat.domain.order.dto.response.OrderResponse;
import com.rocketcrew.pocat.domain.order.entity.Order;
import com.rocketcrew.pocat.domain.order.enums.OrderStatus;
import com.rocketcrew.pocat.domain.order.event.OrderCreatedEvent;
import com.rocketcrew.pocat.domain.order.event.OrderEscalatedEvent;
import com.rocketcrew.pocat.domain.order.repository.OrderRepository;
import com.rocketcrew.pocat.global.exception.common.ErrorCode;
import com.rocketcrew.pocat.global.exception.domain.OrderException;
import com.rocketcrew.pocat.global.metrics.OrderMetrics;
import com.rocketcrew.pocat.global.outbox.service.OutboxEventWriter;
import com.rocketcrew.pocat.support.TestFixtures;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

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

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @Mock
    private OutboxEventWriter outboxEventWriter;

    @Mock
    private AuctionBidRepository auctionBidRepository;

    @Mock
    private SetExpireService setExpireService;

    @Mock
    private AuctionRepository auctionRepository;

    @Mock
    private OrderMetrics metrics;

    @org.junit.jupiter.api.BeforeEach
    void setUp() {
        TransactionSynchronizationManager.initSynchronization();
    }

    @org.junit.jupiter.api.AfterEach
    void tearDown() {
        TransactionSynchronizationManager.clearSynchronization();
    }

    // ── createOrderFromAuction ─────────────────────────────────────────

    @Nested
    @DisplayName("createOrderFromAuction()")
    class CreateOrderFromAuction {

        @Test
        @DisplayName("성공: 주문 저장 후 outbox 기록 및 이벤트 발행")
        void success() {
            Order savedOrder = TestFixtures.anOrder(OrderStatus.PAYMENT_PENDING);
            given(orderRepository.findByAuctionIdAndBidderRank(10L, 1)).willReturn(Optional.empty());
            given(orderRepository.save(any(Order.class))).willReturn(savedOrder);

            orderCommandService.createOrderFromAuction(10L, 3L, 2L, 1L, 10000L, 1);

            verify(orderRepository).save(any(Order.class));
            verify(outboxEventWriter).write(eq("order"), any(), any(OrderCreatedEvent.class));
            verify(eventPublisher).publishEvent(any(OrderCreatedEvent.class));
        }

        @Test
        @DisplayName("성공(멱등): 이미 주문이 존재하면 저장하지 않는다")
        void success_idempotent_alreadyExists() {
            Order existing = TestFixtures.anOrder(OrderStatus.PAYMENT_PENDING);
            given(orderRepository.findByAuctionIdAndBidderRank(10L, 1)).willReturn(Optional.of(existing));

            orderCommandService.createOrderFromAuction(10L, 3L, 2L, 1L, 10000L, 1);

            verify(orderRepository, never()).save(any());
            verify(outboxEventWriter, never()).write(any(), any(), any());
            verify(eventPublisher, never()).publishEvent(any());
        }
    }

    // ── createOrderFromBuyout ──────────────────────────────────────────

    @Nested
    @DisplayName("createOrderFromBuyout()")
    class CreateOrderFromBuyout {

        @Test
        @DisplayName("성공: 즉시구매 주문을 저장한다")
        void success() {
            Order savedOrder = TestFixtures.anBuyoutOrder(OrderStatus.PAYMENT_PENDING);
            given(orderRepository.save(any(Order.class))).willReturn(savedOrder);

            Order result = orderCommandService.createOrderFromBuyout(10L, 3L, 2L, 1L, 10000L);

            verify(orderRepository).save(any(Order.class));
            assertThat(result).isSameAs(savedOrder);
        }
    }

    // ── schedulePaymentDeadline ────────────────────────────────────────

    @Nested
    @DisplayName("schedulePaymentDeadline()")
    class SchedulePaymentDeadline {

        @Test
        @DisplayName("성공: AUTO_PAYMENT_FAILED 상태에서 paymentDeadline=null이면 데드라인을 설정하고 만료 키를 등록한다")
        void success_autoPaymentFailed_noDeadline_setsDeadlineAndSchedulesExpiry() {
            // Arrange: AUTO_PAYMENT_FAILED 상태이나 paymentDeadline이 아직 없는 주문
            Order order = TestFixtures.anOrder(OrderStatus.AUTO_PAYMENT_FAILED);
            ReflectionTestUtils.setField(order, "paymentDeadline", null);
            given(orderRepository.findByOrderUid("ORD-001")).willReturn(Optional.of(order));

            // Act
            orderCommandService.schedulePaymentDeadline("ORD-001");

            // Assert: 데드라인이 설정되고 Redis 만료 키가 등록돼야 한다
            assertThat(order.getPaymentDeadline()).isNotNull();
            verify(setExpireService).scheduleExpiry(eq(1L), any(java.time.Duration.class));
        }

        @Test
        @DisplayName("성공(멱등): paymentDeadline이 이미 있으면 scheduleExpiry를 호출하지 않는다")
        void success_idempotent_deadlineAlreadySet_skipsSchedule() {
            // Arrange: AUTO_PAYMENT_FAILED 상태이고 paymentDeadline이 이미 설정된 주문
            Order order = TestFixtures.aPaymentFailedOrder(); // paymentDeadline = now+30min
            given(orderRepository.findByOrderUid("ORD-001")).willReturn(Optional.of(order));

            // Act
            orderCommandService.schedulePaymentDeadline("ORD-001");

            // Assert: 이미 데드라인이 있으므로 scheduleExpiry 호출 없음
            verify(setExpireService, never()).scheduleExpiry(any(), any());
        }
    }

    // ── escalateToNextRankWithDirectPayment ────────────────────────────

    @Nested
    @DisplayName("escalateToNextRankWithDirectPayment()")
    class EscalateToNextRankWithDirectPayment {

        @Test
        @DisplayName("성공(ESCALATED): 다음 순위 주문이 이미 존재하면 OrderEscalatedEvent(ESCALATED)를 발행한다")
        void escalated_existingNextOrder_publishesEscalatedEvent() {
            Order order = TestFixtures.aPaymentFailedOrder();
            ReflectionTestUtils.setField(order, "bidderRank", 1);
            given(orderRepository.findByOrderUid("ORD-001")).willReturn(Optional.of(order));

            Order nextOrder = TestFixtures.anOrder(OrderStatus.PAYMENT_PENDING);
            ReflectionTestUtils.setField(nextOrder, "orderUid", "ORD-NEXT");
            given(auctionBidRepository.findLostBidderIdsByAuctionIdOrderedByMaxBidPrice(10L))
                    .willReturn(List.of(99L));
            given(orderRepository.findByAuctionIdAndBidderRank(10L, 2)).willReturn(Optional.of(nextOrder));

            EscalationResult result = orderCommandService.escalateToNextRankWithDirectPayment("ORD-001");

            assertThat(result.status()).isEqualTo(EscalationResult.Status.ESCALATED);
            assertThat(result.nextBidderId()).isEqualTo(99L);
            assertThat(result.nextOrderUid()).isEqualTo("ORD-NEXT");

            ArgumentCaptor<OrderEscalatedEvent> captor = ArgumentCaptor.forClass(OrderEscalatedEvent.class);
            verify(eventPublisher).publishEvent(captor.capture());
            OrderEscalatedEvent event = captor.getValue();
            assertThat(event.getStatus()).isEqualTo(EscalationResult.Status.ESCALATED);
            assertThat(event.getNextBidderId()).isEqualTo(99L);
            assertThat(event.getNextOrderUid()).isEqualTo("ORD-NEXT");
            assertThat(event.getSellerId()).isEqualTo(order.getSellerId());
            assertThat(event.getOrderUid()).isEqualTo("ORD-001");
        }

        @Test
        @DisplayName("성공(CANCELLED): 다음 입찰자가 없으면 경매를 취소하고 OrderEscalatedEvent(CANCELLED)를 발행한다")
        void cancelled_noNextBidder_publishesCancelledEvent() {
            Order order = TestFixtures.aPaymentFailedOrder();
            ReflectionTestUtils.setField(order, "bidderRank", 1);
            given(orderRepository.findByOrderUid("ORD-001")).willReturn(Optional.of(order));
            given(auctionBidRepository.findLostBidderIdsByAuctionIdOrderedByMaxBidPrice(10L))
                    .willReturn(List.of());
            given(auctionRepository.findById(10L)).willReturn(Optional.empty());

            EscalationResult result = orderCommandService.escalateToNextRankWithDirectPayment("ORD-001");

            assertThat(result.status()).isEqualTo(EscalationResult.Status.CANCELLED);

            ArgumentCaptor<OrderEscalatedEvent> captor = ArgumentCaptor.forClass(OrderEscalatedEvent.class);
            verify(eventPublisher).publishEvent(captor.capture());
            OrderEscalatedEvent event = captor.getValue();
            assertThat(event.getStatus()).isEqualTo(EscalationResult.Status.CANCELLED);
            assertThat(event.getNextBidderId()).isNull();
            assertThat(event.getNextOrderUid()).isNull();
            assertThat(event.getSellerId()).isEqualTo(order.getSellerId());
            assertThat(event.getOrderUid()).isEqualTo("ORD-001");
        }

        @Test
        @DisplayName("성공(ESCALATED): 신규 2등 주문 생성 시 2등 입찰자의 입찰가로 finalPrice가 설정된다")
        void escalated_newNextOrder_usesBidderPrice() {
            // 1등 주문: finalPrice=10000L
            Order firstOrder = TestFixtures.aPaymentFailedOrder();
            ReflectionTestUtils.setField(firstOrder, "bidderRank", 1);
            given(orderRepository.findByOrderUid("ORD-001")).willReturn(Optional.of(firstOrder));

            // 2등 입찰자: userId=99L, 최고 입찰가=8000L (1등보다 낮음)
            given(auctionBidRepository.findLostBidderIdsByAuctionIdOrderedByMaxBidPrice(10L))
                    .willReturn(List.of(99L));
            given(orderRepository.findByAuctionIdAndBidderRank(10L, 2)).willReturn(Optional.empty());

            AuctionBid secondBid = AuctionBid.builder()
                    .auctionId(10L)
                    .userId(99L)
                    .bidPrice(8000L)
                    .status(BidStatus.LOST)
                    .build();
            given(auctionBidRepository.findFirstByAuctionIdAndUserIdAndStatusOrderByBidPriceDescCreatedAtDesc(
                    10L, 99L, BidStatus.LOST)).willReturn(Optional.of(secondBid));

            ArgumentCaptor<Order> savedOrderCaptor = ArgumentCaptor.forClass(Order.class);
            Order savedNextOrder = TestFixtures.anOrder(OrderStatus.PAYMENT_PENDING);
            ReflectionTestUtils.setField(savedNextOrder, "orderUid", "ORD-002");
            given(orderRepository.save(savedOrderCaptor.capture())).willReturn(savedNextOrder);
            given(orderRepository.findByOrderUid("ORD-002")).willReturn(Optional.of(savedNextOrder));

            EscalationResult result = orderCommandService.escalateToNextRankWithDirectPayment("ORD-001");

            assertThat(result.status()).isEqualTo(EscalationResult.Status.ESCALATED);
            // 저장된 주문의 finalPrice가 2등 입찰가(8000L)여야 함 — 1등 낙찰가(10000L)가 아님
            Order capturedOrder = savedOrderCaptor.getAllValues().get(0);
            assertThat(capturedOrder.getFinalPrice()).isEqualTo(8000L);
            assertThat(capturedOrder.getBuyerId()).isEqualTo(99L);
        }

        @Test
        @DisplayName("성공(SKIPPED): 승격 불가 상태이면 이벤트를 발행하지 않는다")
        void skipped_invalidStatus_doesNotPublishEvent() {
            Order order = TestFixtures.anOrder(OrderStatus.PAYMENT_COMPLETED);
            given(orderRepository.findByOrderUid("ORD-001")).willReturn(Optional.of(order));

            EscalationResult result = orderCommandService.escalateToNextRankWithDirectPayment("ORD-001");

            assertThat(result.status()).isEqualTo(EscalationResult.Status.SKIPPED);
            verify(eventPublisher, never()).publishEvent(any(OrderEscalatedEvent.class));
        }
    }

}
