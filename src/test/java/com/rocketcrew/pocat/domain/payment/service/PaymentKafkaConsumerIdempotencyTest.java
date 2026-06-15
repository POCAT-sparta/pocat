package com.rocketcrew.pocat.domain.payment.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rocketcrew.pocat.cache.MockRedisTestConfig;
import com.rocketcrew.pocat.domain.auction.repository.AuctionSearchRepository;
import com.rocketcrew.pocat.domain.card.repository.CardSearchRepository;
import com.rocketcrew.pocat.domain.auction.service.AuctionEsIndexService;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import com.rocketcrew.pocat.domain.auction.kafka.AuctionEventHandler;
import com.rocketcrew.pocat.domain.bid.service.BidEventHandler;
import com.rocketcrew.pocat.domain.order.service.OrderEventHandler;
import com.rocketcrew.pocat.domain.payment.client.out.kafka.handler.PaymentEventHandler;
import com.rocketcrew.pocat.domain.refund.service.RefundEventHandler;
import com.rocketcrew.pocat.domain.notification.service.NotificationEventHandler;
import com.rocketcrew.pocat.domain.order.entity.Order;
import com.rocketcrew.pocat.domain.order.enums.OrderStatus;
import com.rocketcrew.pocat.domain.order.enums.OrderType;
import com.rocketcrew.pocat.domain.order.repository.OrderRepository;
import com.rocketcrew.pocat.domain.payment.client.out.kafka.consumer.PaymentKafkaConsumer;
import com.rocketcrew.pocat.domain.payment.client.out.portone.PortOneClientService;
import com.rocketcrew.pocat.domain.payment.client.out.portone.PortOneStatus;
import com.rocketcrew.pocat.domain.payment.client.out.portone.dto.PortOnePaymentResponse;
import com.rocketcrew.pocat.domain.user.entity.User;
import com.rocketcrew.pocat.domain.user.enums.UserRole;
import com.rocketcrew.pocat.domain.user.repository.UserRepository;
import com.rocketcrew.pocat.global.outbox.service.OutboxEventWriter;
import com.rocketcrew.pocat.global.ratelimit.RedisRateLimiter;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.Tag;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Kafka consumer 멱등성 통합 테스트
 *
 * PaymentKafkaConsumer.consume()을 직접 호출해
 * 동일 메시지 중복 소비 시 결제가 1건만 생성되는지 검증한다.
 * 실제 Kafka 브로커 없이 consumer 메서드를 직접 호출하는 방식으로 격리한다.
 */
@Tag("concurrency")
@SpringBootTest
@Import(MockRedisTestConfig.class)
@DisplayName("Kafka consumer 멱등성 통합 테스트")
class PaymentKafkaConsumerIdempotencyTest {

    // ── 외부 의존성 Mock ─────────────────────────────────────────
    @MockBean private StringRedisTemplate stringRedisTemplate;
    @MockBean private RedissonClient redissonClient;
    @MockBean private RedisRateLimiter redisRateLimiter;
    @MockBean private PortOneClientService portOneClientService;
    @MockBean private OutboxEventWriter outboxEventWriter;
    @MockBean private AuctionSearchRepository auctionSearchRepository;
    @MockBean private CardSearchRepository cardSearchRepository;
    @MockBean private RedisConnectionFactory redisConnectionFactory;
    @MockBean private RedisMessageListenerContainer redisMessageListenerContainer;
    @MockBean private AuctionEsIndexService auctionEsIndexService;
    @MockBean private AuctionEventHandler auctionEventHandler;
    @MockBean private BidEventHandler bidEventHandler;
    @MockBean private OrderEventHandler orderEventHandler;
    @MockBean private PaymentEventHandler paymentEventHandler;
    @MockBean private RefundEventHandler refundEventHandler;
    @MockBean private NotificationEventHandler notificationEventHandler;

    // ── 실제 빈 ──────────────────────────────────────────────────
    @Autowired private PaymentKafkaConsumer paymentKafkaConsumer;
    @Autowired private OrderRepository orderRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private TransactionTemplate transactionTemplate;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private ObjectMapper objectMapper;

    private static final String TEST_EMAIL      = "it-kafka-buyer@test.com";
    private static final String TEST_ORDER_UID  = "IT-KAFKA-ORDER-001";
    private static final String TEST_BILLING_KEY = "kafka-test-billing-key";

    private Long buyerId;
    private Long orderId;

    @BeforeEach
    void setUp() {
        PortOnePaymentResponse paidResponse = PortOnePaymentResponse.builder()
                .status(PortOneStatus.PAID)
                .amount(10_000L)
                .paymentMethod("card")
                .paidAt(LocalDateTime.now())
                .build();
        when(portOneClientService.attemptBillingKeyPayment(anyString(), anyString(), anyLong()))
                .thenReturn(paidResponse);
        when(portOneClientService.getPayment(anyString()))
                .thenReturn(paidResponse);

        transactionTemplate.execute(status -> {
            User buyer = userRepository.save(User.builder()
                    .email(TEST_EMAIL)
                    .password("encoded-pw")
                    .nickname("카프카멱등테스트구매자")
                    .userRole(UserRole.USER)
                    .billingKey(TEST_BILLING_KEY)
                    .build());
            buyerId = buyer.getId();

            Order order = orderRepository.save(Order.builder()
                    .auctionId(200L)
                    .cardId(1L)
                    .sellerId(2L)
                    .buyerId(buyerId)
                    .orderUid(TEST_ORDER_UID)
                    .finalPrice(10_000L)
                    .status(OrderStatus.PAYMENT_PENDING)
                    .orderType(OrderType.AUCTION)
                    .build());
            orderId = order.getId();
            return null;
        });
    }

    @AfterEach
    void tearDown() {
        jdbcTemplate.update("DELETE FROM payments WHERE order_id = ?", orderId);
        jdbcTemplate.update("DELETE FROM orders WHERE order_uid = ?", TEST_ORDER_UID);
        jdbcTemplate.update("DELETE FROM users WHERE email = ?", TEST_EMAIL);
    }

    @Nested
    @DisplayName("consume() 멱등성")
    class ConsumeIdempotency {

        @Test
        @DisplayName("동일 order.created 이벤트 2회 소비 → 결제 1건, PortOne 1회 호출")
        void consume_sameOrderCreatedTwice_createsOnlyOnePayment() throws Exception {
            Acknowledgment ack = mock(Acknowledgment.class);

            String message = objectMapper.writeValueAsString(Map.of(
                    "eventType", "order.created",
                    "orderUid", TEST_ORDER_UID,
                    "buyerId", buyerId,
                    "sellerId", 2,
                    "finalPrice", 10_000
            ));

            // 동일 메시지 2회 소비 (네트워크 재전송 시뮬레이션)
            paymentKafkaConsumer.consume(message, ack);
            paymentKafkaConsumer.consume(message, ack);

            Long paymentCount = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM payments WHERE order_id = ?",
                    Long.class, orderId);

            assertThat(paymentCount)
                    .as("중복 소비 시 결제는 1건만 생성되어야 함")
                    .isEqualTo(1L);

            // autoPayment 3단계 멱등성으로 PortOne 호출은 1회만
            verify(portOneClientService, times(1))
                    .attemptBillingKeyPayment(anyString(), eq(TEST_BILLING_KEY), eq(10_000L));

            // 두 호출 모두 정상 처리 후 ack
            verify(ack, times(2)).acknowledge();
        }

        @Test
        @DisplayName("order.created 아닌 이벤트는 autoPayment 미호출 후 ack")
        void consume_nonOrderCreatedEvent_skipsAutoPayment() throws Exception {
            Acknowledgment ack = mock(Acknowledgment.class);

            String message = objectMapper.writeValueAsString(Map.of(
                    "eventType", "order.cancelled",
                    "orderUid", TEST_ORDER_UID,
                    "buyerId", buyerId
            ));

            paymentKafkaConsumer.consume(message, ack);

            verify(portOneClientService, never())
                    .attemptBillingKeyPayment(anyString(), anyString(), anyLong());
            verify(ack, times(1)).acknowledge();
        }
    }
}
