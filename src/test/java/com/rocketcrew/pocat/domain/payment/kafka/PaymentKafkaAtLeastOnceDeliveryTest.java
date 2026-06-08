package com.rocketcrew.pocat.domain.payment.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rocketcrew.pocat.cache.MockRedisTestConfig;
import com.rocketcrew.pocat.domain.auction.repository.AuctionSearchRepository;
import com.rocketcrew.pocat.domain.card.repository.CardSearchRepository;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import com.rocketcrew.pocat.domain.auction.kafka.AuctionEventHandler;
import com.rocketcrew.pocat.domain.bid.service.BidEventHandler;
import com.rocketcrew.pocat.domain.order.service.OrderEventHandler;
import com.rocketcrew.pocat.domain.payment.client.out.kafka.handler.PaymentEventHandler;
import com.rocketcrew.pocat.domain.refund.service.RefundEventHandler;
import com.rocketcrew.pocat.domain.settlement.service.SettlementEventHandler;
import com.rocketcrew.pocat.domain.notification.service.NotificationEventHandler;
import com.rocketcrew.pocat.domain.order.entity.Order;
import com.rocketcrew.pocat.domain.order.enums.DeliveryStatus;
import com.rocketcrew.pocat.domain.order.enums.OrderStatus;
import com.rocketcrew.pocat.domain.order.enums.OrderType;
import com.rocketcrew.pocat.domain.order.repository.OrderRepository;
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
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Kafka at-least-once 전달 멱등성 통합 테스트
 *
 * 실제 Kafka 브로커(Testcontainers confluentinc/cp-kafka)를 사용해
 * 네트워크 재전달로 동일 메시지가 2회 소비되어도 결제 1건만 생성됨을 검증한다.
 *
 * ─────────────────────────────────────────────────────────────
 * PaymentKafkaConsumerIdempotencyTest와의 차이점
 * ─────────────────────────────────────────────────────────────
 * - consumer 메서드를 직접 호출하는 것이 아니라
 *   실제 Kafka 브로커에 메시지를 발행하고 @KafkaListener가 소비하는 E2E 경로
 * - 실제 오프셋 커밋 · 수동 Ack(MANUAL) · 재전달 동작까지 검증
 * ─────────────────────────────────────────────────────────────
 */
@Tag("bulk")
@Tag("integration")
@SpringBootTest
@Testcontainers
@Import(MockRedisTestConfig.class)
@DisplayName("Kafka at-least-once 전달 멱등성 통합 테스트")
class PaymentKafkaAtLeastOnceDeliveryTest {

    // ── Testcontainers: 클래스 단위로 컨테이너 재사용 ─────────────
    @Container
    static final KafkaContainer kafka =
            new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.4.0"));

    @DynamicPropertySource
    static void overrideKafkaProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.kafka.bootstrap-servers", kafka::getBootstrapServers);
        // 단일 브로커 환경에서 replicas(3) NewTopic 생성 실패를 무시
        registry.add("spring.kafka.admin.fail-fast", () -> "false");
    }

    // ── 외부 의존성 Mock (Kafka만 실제 사용) ─────────────────────
    @MockBean private StringRedisTemplate stringRedisTemplate;
    @MockBean private RedissonClient redissonClient;
    @MockBean private RedisRateLimiter redisRateLimiter;
    @MockBean private PortOneClientService portOneClientService;
    @MockBean private OutboxEventWriter outboxEventWriter;
    @MockBean private AuctionSearchRepository auctionSearchRepository;
    @MockBean private CardSearchRepository cardSearchRepository;
    @MockBean private RedisConnectionFactory redisConnectionFactory;
    @MockBean private RedisMessageListenerContainer redisMessageListenerContainer;
    @MockBean private AuctionEventHandler auctionEventHandler;
    @MockBean private BidEventHandler bidEventHandler;
    @MockBean private OrderEventHandler orderEventHandler;
    @MockBean private PaymentEventHandler paymentEventHandler;
    @MockBean private RefundEventHandler refundEventHandler;
    @MockBean private SettlementEventHandler settlementEventHandler;
    @MockBean private NotificationEventHandler notificationEventHandler;

    // ── 실제 빈 ──────────────────────────────────────────────────
    // 동일 키의 메시지는 동일 파티션으로 라우팅 → 순차 소비 보장
    @Autowired @Qualifier("kafkaTemplate")
    private KafkaTemplate<String, String> kafkaTemplate;

    @Autowired private OrderRepository orderRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private TransactionTemplate transactionTemplate;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private ObjectMapper objectMapper;

    private static final String TEST_EMAIL       = "it-tc-kafka-buyer@test.com";
    private static final String TEST_ORDER_UID   = "IT-TC-KAFKA-ORDER-001";
    private static final String TEST_BILLING_KEY = "tc-kafka-billing-key";

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
                    .nickname("TC카프카테스트구매자")
                    .userRole(UserRole.USER)
                    .billingKey(TEST_BILLING_KEY)
                    .build());
            buyerId = buyer.getId();

            Order order = orderRepository.save(Order.builder()
                    .auctionId(500L)
                    .cardId(1L)
                    .sellerId(2L)
                    .buyerId(buyerId)
                    .orderUid(TEST_ORDER_UID)
                    .finalPrice(10_000L)
                    .status(OrderStatus.PAYMENT_PENDING)
                    .deliveryStatus(DeliveryStatus.PREPARING)
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

    @Test
    @DisplayName("동일 order.created 메시지 2회 발행 → at-least-once에도 결제 1건, PortOne 1회 호출")
    void kafka_atLeastOnceDelivery_createsOnlyOnePayment() throws Exception {
        String message = objectMapper.writeValueAsString(Map.of(
                "eventType", "order.created",
                "orderUid", TEST_ORDER_UID,
                "buyerId", buyerId,
                "sellerId", 2,
                "finalPrice", 10_000
        ));

        // 동일 메시지 2회 발행 — 같은 key로 보내 동일 파티션으로 라우팅 (순차 소비 보장)
        kafkaTemplate.send("order", TEST_ORDER_UID, message).get();
        kafkaTemplate.send("order", TEST_ORDER_UID, message).get();

        // 2개 메시지가 모두 소비되어 안정적으로 처리될 때까지 대기한다.
        // COMPLETED 1건을 확인한 후 추가 2초를 대기해 두 번째 메시지 처리가 완료되었음을 보장한다.
        // (두 번째 소비 후 중복 결제나 추가 PortOne 호출이 없는지까지 검증)
        await().atMost(Duration.ofSeconds(30))
                .pollInterval(Duration.ofMillis(500))
                .until(() -> {
                    Long count = jdbcTemplate.queryForObject(
                            "SELECT COUNT(*) FROM payments WHERE order_id = ? AND status = 'COMPLETED'",
                            Long.class, orderId);
                    return count != null && count == 1L;
                });

        // 첫 번째 메시지가 처리된 뒤, 두 번째(중복) 메시지가 소비·처리될 충분한 시간을 대기
        Thread.sleep(2_000);

        Long paymentCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM payments WHERE order_id = ?", Long.class, orderId);

        assertThat(paymentCount)
                .as("at-least-once 재전달에도 결제는 1건만 생성되어야 함")
                .isEqualTo(1L);

        // autoPayment 3단계 멱등성으로 PortOne 호출은 정확히 1회
        verify(portOneClientService, times(1))
                .attemptBillingKeyPayment(anyString(), eq(TEST_BILLING_KEY), eq(10_000L));
    }
}
