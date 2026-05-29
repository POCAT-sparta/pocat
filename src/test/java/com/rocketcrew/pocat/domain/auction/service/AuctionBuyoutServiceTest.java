package com.rocketcrew.pocat.domain.auction.service;

import com.rocketcrew.pocat.domain.auction.dto.response.BuyoutAuctionResponse;
import com.rocketcrew.pocat.domain.auction.entity.Auction;
import com.rocketcrew.pocat.domain.auction.enums.AuctionStatus;
import com.rocketcrew.pocat.domain.auction.event.AuctionBuyoutCompletedEvent;
import com.rocketcrew.pocat.domain.auction.repository.AuctionRepository;
import com.rocketcrew.pocat.domain.bid.entity.AuctionBid;
import com.rocketcrew.pocat.domain.bid.enums.BidStatus;
import com.rocketcrew.pocat.domain.bid.repository.AuctionBidRepository;
import com.rocketcrew.pocat.domain.order.entity.Order;
import com.rocketcrew.pocat.domain.order.enums.DeliveryStatus;
import com.rocketcrew.pocat.domain.order.enums.OrderStatus;
import com.rocketcrew.pocat.domain.order.repository.OrderRepository;
import com.rocketcrew.pocat.domain.order.service.OrderCommandService;
import com.rocketcrew.pocat.domain.order.service.OrderQueryService;
import com.rocketcrew.pocat.domain.payment.dto.response.PaymentResponse;
import com.rocketcrew.pocat.domain.payment.entity.PaymentStatus;
import com.rocketcrew.pocat.domain.payment.entity.PaymentType;
import com.rocketcrew.pocat.domain.payment.repository.PaymentRepository;
import com.rocketcrew.pocat.domain.user.entity.User;
import com.rocketcrew.pocat.domain.user.enums.UserRole;
import com.rocketcrew.pocat.domain.user.service.UserQueryService;
import com.rocketcrew.pocat.global.outbox.service.OutboxEventWriter;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class AuctionBuyoutServiceTest {

    AuctionBuyoutService service;

    AuctionBuyoutTransactionService buyoutTransactionService;

    @Mock
    AuctionRepository auctionRepository;

    @Mock
    AuctionBidRepository auctionBidRepository;

    @Mock
    OrderRepository orderRepository;

    @Mock
    PaymentRepository paymentRepository;

    @Mock
    OrderCommandService orderCommandService;

    @Mock
    OrderQueryService orderQueryService;

    @Mock
    UserQueryService userQueryService;

    @Mock
    RedissonClient redissonClient;

    @Mock
    RLock rLock;

    @Mock
    ApplicationEventPublisher eventPublisher;

    @Mock
    OutboxEventWriter outboxEventWriter;

    @BeforeEach
    void setUp() throws InterruptedException {
        buyoutTransactionService = new AuctionBuyoutTransactionService(auctionRepository, auctionBidRepository);
        service = new AuctionBuyoutService(
                orderRepository,
                paymentRepository,
                orderCommandService,
                orderQueryService,
                userQueryService,
                redissonClient,
                eventPublisher,
                outboxEventWriter,
                buyoutTransactionService
        );

        given(redissonClient.getLock(anyString())).willReturn(rLock);
        given(rLock.tryLock(anyLong(), any(TimeUnit.class))).willReturn(true);
        given(rLock.isHeldByCurrentThread()).willReturn(true);
        TransactionSynchronizationManager.initSynchronization();
    }

    @AfterEach
    void tearDown() {
        TransactionSynchronizationManager.clearSynchronization();
    }

    @Test
    @DisplayName("즉시구매 요청 시 즉시구매가로 최고 입찰을 생성하고 주문을 생성한다")
    void buyout_createsLeadingBidWithBuyoutPriceAndOrder() {
        // given
        User buyer = User.builder()
                .email("buyer@test.com")
                .password("encoded")
                .nickname("buyer")
                .userRole(UserRole.USER)
                .billingKey("billing-key")
                .build();
        ReflectionTestUtils.setField(buyer, "id", 1L);

        Auction auction = Auction.builder()
                .cardId(3L)
                .sellerId(2L)
                .highestBidderId(4L)
                .title("test auction")
                .startingPrice(1000L)
                .buyoutPrice(10000L)
                .highestPrice(5000L)
                .status(AuctionStatus.ACTIVE)
                .startedAt(LocalDateTime.now().minusHours(1))
                .endedAt(LocalDateTime.now().plusHours(1))
                .build();
        ReflectionTestUtils.setField(auction, "id", 10L);

        AuctionBid previousLeadingBid = AuctionBid.builder()
                .auctionId(10L)
                .userId(4L)
                .bidPrice(5000L)
                .status(BidStatus.LEADING)
                .build();

        given(userQueryService.getUserEntity(1L)).willReturn(buyer);
        given(auctionRepository.findById(10L)).willReturn(Optional.of(auction));
        Order order = Order.builder()
                .auctionId(10L)
                .cardId(3L)
                .sellerId(2L)
                .buyerId(1L)
                .orderUid("ORD-001")
                .finalPrice(10000L)
                .status(OrderStatus.PAYMENT_COMPLETED)
                .deliveryStatus(DeliveryStatus.PREPARING)
                .build();
        ReflectionTestUtils.setField(order, "id", 20L);

        PaymentResponse payment = new PaymentResponse(
                "PAY-001",
                20L,
                10000L,
                PaymentType.BILLING_KEY,
                "card",
                PaymentStatus.COMPLETED,
                LocalDateTime.now(),
                LocalDateTime.now()
        );
        given(orderCommandService.createOrderFromBuyout(10L, 3L, 2L, 1L, 10000L)).willReturn(payment);
        given(orderQueryService.findByOrderid(20L)).willReturn(order);
        given(auctionBidRepository.findFirstByAuctionIdAndUserIdAndStatusOrderByBidPriceDescCreatedAtDesc(
                10L, 1L, BidStatus.WON
        )).willReturn(Optional.empty());
        given(auctionBidRepository.findAllByAuctionId(10L)).willReturn(List.of(previousLeadingBid));
        given(auctionBidRepository.save(any(AuctionBid.class))).willAnswer(invocation -> {
            AuctionBid bid = invocation.getArgument(0);
            ReflectionTestUtils.setField(bid, "id", 30L);
            return bid;
        });

        // when
        BuyoutAuctionResponse response = service.buyout(1L, 10L);

        // then
        assertThat(response.auctionId()).isEqualTo(10L);
        assertThat(response.bidId()).isEqualTo(30L);
        assertThat(response.orderUid()).isEqualTo("ORD-001");
        assertThat(response.paymentUid()).isEqualTo("PAY-001");
        assertThat(response.paymentStatus()).isEqualTo(PaymentStatus.COMPLETED.name());
        assertThat(response.paidAmount()).isEqualTo(10000L);
        assertThat(response.auctionStatus()).isEqualTo(AuctionStatus.ENDED.name());
        assertThat(auction.getStatus()).isEqualTo(AuctionStatus.ENDED);
        assertThat(auction.getHighestBidderId()).isEqualTo(1L);
        assertThat(auction.getHighestPrice()).isEqualTo(10000L);
        assertThat(previousLeadingBid.getStatus()).isEqualTo(BidStatus.LOST);

        ArgumentCaptor<AuctionBid> bidCaptor = ArgumentCaptor.forClass(AuctionBid.class);
        verify(auctionBidRepository).save(bidCaptor.capture());
        AuctionBid savedBid = bidCaptor.getValue();
        assertThat(savedBid.getBidPrice()).isEqualTo(10000L);
        assertThat(savedBid.getStatus()).isEqualTo(BidStatus.WON);

        verify(orderCommandService).createOrderFromBuyout(10L, 3L, 2L, 1L, 10000L);
        verify(rLock).unlock();

        ArgumentCaptor<Object> eventCaptor = ArgumentCaptor.forClass(Object.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());
        assertThat(eventCaptor.getAllValues()).anySatisfy(publishedEvent -> {
            assertThat(publishedEvent).isInstanceOf(AuctionBuyoutCompletedEvent.class);
        });
        AuctionBuyoutCompletedEvent event = eventCaptor.getAllValues().stream()
                .filter(AuctionBuyoutCompletedEvent.class::isInstance)
                .map(AuctionBuyoutCompletedEvent.class::cast)
                .findFirst()
                .orElseThrow();
        assertThat(event.getAuctionId()).isEqualTo(10L);
        assertThat(event.getOrderId()).isEqualTo(20L);
        assertThat(event.getOrderUid()).isEqualTo("ORD-001");
        assertThat(event.getBuyerId()).isEqualTo(1L);
        assertThat(event.getSellerId()).isEqualTo(2L);
        assertThat(event.getCardId()).isEqualTo(3L);
        assertThat(event.getFinalPrice()).isEqualTo(10000L);
        assertThat(event.getPreviousHighestBidderId()).isEqualTo(4L);
    }

    @Test
    @DisplayName("즉시구매 주문 생성이 실패하면 경매를 ACTIVE로 되돌리고 입찰을 생성하지 않는다")
    void buyout_restoresAuctionWhenOrderCreationFails() {
        // given
        User buyer = User.builder()
                .email("buyer@test.com")
                .password("encoded")
                .nickname("buyer")
                .userRole(UserRole.USER)
                .billingKey("billing-key")
                .build();
        ReflectionTestUtils.setField(buyer, "id", 1L);

        Auction auction = Auction.builder()
                .cardId(3L)
                .sellerId(2L)
                .title("test auction")
                .startingPrice(1000L)
                .buyoutPrice(10000L)
                .highestPrice(5000L)
                .status(AuctionStatus.ACTIVE)
                .startedAt(LocalDateTime.now().minusHours(1))
                .endedAt(LocalDateTime.now().plusHours(1))
                .build();
        ReflectionTestUtils.setField(auction, "id", 10L);

        RuntimeException paymentFailure = new RuntimeException("payment failed");
        given(userQueryService.getUserEntity(1L)).willReturn(buyer);
        given(auctionRepository.findById(10L)).willReturn(Optional.of(auction));
        given(orderCommandService.createOrderFromBuyout(10L, 3L, 2L, 1L, 10000L))
                .willThrow(paymentFailure);

        // when & then
        assertThatThrownBy(() -> service.buyout(1L, 10L))
                .isSameAs(paymentFailure);

        assertThat(auction.getStatus()).isEqualTo(AuctionStatus.ACTIVE);
        assertThat(auction.getHighestBidderId()).isNull();
        assertThat(auction.getHighestPrice()).isEqualTo(5000L);
        verify(auctionBidRepository, never()).save(any(AuctionBid.class));
        verify(redissonClient).getLock("auction:lock:10");
        verify(rLock).unlock();
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    @DisplayName("즉시구매 결제 실패 주문이 반환되면 경매를 ACTIVE로 되돌리고 입찰을 생성하지 않는다")
    void buyout_restoresAuctionWhenPaymentFailedOrderReturned() {
        // given
        User buyer = User.builder()
                .email("buyer@test.com")
                .password("encoded")
                .nickname("buyer")
                .userRole(UserRole.USER)
                .billingKey("billing-key")
                .build();
        ReflectionTestUtils.setField(buyer, "id", 1L);

        Auction auction = Auction.builder()
                .cardId(3L)
                .sellerId(2L)
                .title("test auction")
                .startingPrice(1000L)
                .buyoutPrice(10000L)
                .highestPrice(5000L)
                .status(AuctionStatus.ACTIVE)
                .startedAt(LocalDateTime.now().minusHours(1))
                .endedAt(LocalDateTime.now().plusHours(1))
                .build();
        ReflectionTestUtils.setField(auction, "id", 10L);

        given(userQueryService.getUserEntity(1L)).willReturn(buyer);
        given(auctionRepository.findById(10L)).willReturn(Optional.of(auction));
        PaymentResponse payment = new PaymentResponse(
                "PAY-FAILED",
                20L,
                10000L,
                PaymentType.BILLING_KEY,
                null,
                PaymentStatus.FAILED,
                null,
                LocalDateTime.now()
        );
        Order failedOrder = Order.builder()
                .auctionId(10L)
                .cardId(3L)
                .sellerId(2L)
                .buyerId(1L)
                .orderUid("ORD-FAILED")
                .finalPrice(10000L)
                .status(OrderStatus.AUTO_PAYMENT_FAILED)
                .deliveryStatus(DeliveryStatus.PREPARING)
                .build();
        ReflectionTestUtils.setField(failedOrder, "id", 20L);
        given(orderCommandService.createOrderFromBuyout(10L, 3L, 2L, 1L, 10000L)).willReturn(payment);
        given(orderQueryService.findByOrderid(20L)).willReturn(failedOrder);

        // when & then
        assertThatThrownBy(() -> service.buyout(1L, 10L))
                .isInstanceOf(com.rocketcrew.pocat.global.exception.domain.AuctionException.class);
        assertThat(auction.getStatus()).isEqualTo(AuctionStatus.ACTIVE);
        assertThat(auction.getHighestBidderId()).isNull();
        assertThat(auction.getHighestPrice()).isEqualTo(5000L);
        verify(auctionBidRepository, never()).save(any(AuctionBid.class));
        verify(orderCommandService).createOrderFromBuyout(10L, 3L, 2L, 1L, 10000L);
        verify(orderQueryService).findByOrderid(20L);
        verify(rLock).unlock();
        verify(eventPublisher, never()).publishEvent(any());
    }
}
