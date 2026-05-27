package com.rocketcrew.pocat.support;

import com.rocketcrew.pocat.domain.card.entity.Card;
import com.rocketcrew.pocat.domain.card.entity.enums.CardCategory;
import com.rocketcrew.pocat.domain.card.entity.enums.CardGrade;
import com.rocketcrew.pocat.domain.card.entity.enums.CardSource;
import com.rocketcrew.pocat.domain.card.entity.enums.CardStatus;
import com.rocketcrew.pocat.domain.series.entity.Series;
import com.rocketcrew.pocat.domain.set.entity.PokemonSet;
import com.rocketcrew.pocat.domain.order.entity.Order;
import com.rocketcrew.pocat.domain.order.enums.DeliveryStatus;
import com.rocketcrew.pocat.domain.order.enums.OrderStatus;
import com.rocketcrew.pocat.domain.payment.entity.Payment;
import com.rocketcrew.pocat.domain.payment.entity.PaymentStatus;
import com.rocketcrew.pocat.domain.payment.entity.PaymentType;
import com.rocketcrew.pocat.domain.user.entity.User;
import com.rocketcrew.pocat.domain.user.enums.UserRole;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;

/**
 * 테스트 픽스처 유틸리티.
 * 모든 엔티티 ID는 ReflectionTestUtils.setField 로 주입한다
 * (BaseEntity.id 가 @GeneratedValue 이므로 직접 생성자 접근 불가).
 */
public final class TestFixtures {

    private TestFixtures() {}

    // ── User ─────────────────────────────────────────────────────────

    /** id=1L, role USER, billingKey=null, isBidBlocked=false */
    public static User aUser() {
        User user = User.builder()
                .email("buyer@test.com")
                .password("encoded-pw")
                .nickname("구매자")
                .userRole(UserRole.USER)
                .build();
        ReflectionTestUtils.setField(user, "id", 1L);
        return user;
    }

    /** id=2L, role ADMIN */
    public static User anAdmin() {
        User admin = User.builder()
                .email("admin@test.com")
                .password("encoded-pw")
                .nickname("관리자")
                .userRole(UserRole.ADMIN)
                .build();
        ReflectionTestUtils.setField(admin, "id", 2L);
        return admin;
    }

    /** id=1L, role USER, billingKey="bkey-001" */
    public static User aUserWithBillingKey() {
        User user = User.builder()
                .email("buyer@test.com")
                .password("encoded-pw")
                .nickname("구매자")
                .userRole(UserRole.USER)
                .billingKey("bkey-001")
                .build();
        ReflectionTestUtils.setField(user, "id", 1L);
        return user;
    }

    /** id=1L, role USER, isBidBlocked=true */
    public static User aBidBlockedUser() {
        User user = User.builder()
                .email("blocked@test.com")
                .password("encoded-pw")
                .nickname("차단된사용자")
                .userRole(UserRole.USER)
                .isBidBlocked(true)
                .build();
        ReflectionTestUtils.setField(user, "id", 1L);
        return user;
    }

    // ── Order ────────────────────────────────────────────────────────

    /**
     * id=1L, orderUid="ORD-001", buyerId=1L, sellerId=2L, cardId=3L,
     * finalPrice=10000L, deliveryStatus=PREPARING
     */
    public static Order anOrder(OrderStatus status) {
        Order order = Order.builder()
                .auctionId(10L)
                .cardId(3L)
                .sellerId(2L)
                .buyerId(1L)
                .orderUid("ORD-001")
                .finalPrice(10000L)
                .status(status)
                .deliveryStatus(DeliveryStatus.PREPARING)
                .build();
        ReflectionTestUtils.setField(order, "id", 1L);
        ReflectionTestUtils.setField(order, "createdAt", LocalDateTime.now().minusHours(1));
        ReflectionTestUtils.setField(order, "updatedAt", LocalDateTime.now().minusMinutes(30));
        return order;
    }

    /**
     * PAYMENT_FAILED 상태의 주문. updatedAt=30분 전 (결제 가능 시간 내).
     */
    public static Order aPaymentFailedOrder() {
        Order order = Order.builder()
                .auctionId(10L)
                .cardId(3L)
                .sellerId(2L)
                .buyerId(1L)
                .orderUid("ORD-001")
                .finalPrice(10000L)
                .status(OrderStatus.PAYMENT_FAILED)
                .deliveryStatus(DeliveryStatus.PREPARING)
                .build();
        ReflectionTestUtils.setField(order, "id", 1L);
        ReflectionTestUtils.setField(order, "createdAt", LocalDateTime.now().minusHours(2));
        ReflectionTestUtils.setField(order, "updatedAt", LocalDateTime.now().minusMinutes(30));
        return order;
    }

    /**
     * PAYMENT_FAILED 상태이지만 updatedAt=2시간 전 (결제 가능 시간 초과).
     */
    public static Order anExpiredPaymentFailedOrder() {
        Order order = Order.builder()
                .auctionId(10L)
                .cardId(3L)
                .sellerId(2L)
                .buyerId(1L)
                .orderUid("ORD-001")
                .finalPrice(10000L)
                .status(OrderStatus.PAYMENT_FAILED)
                .deliveryStatus(DeliveryStatus.PREPARING)
                .build();
        ReflectionTestUtils.setField(order, "id", 1L);
        ReflectionTestUtils.setField(order, "createdAt", LocalDateTime.now().minusHours(3));
        ReflectionTestUtils.setField(order, "updatedAt", LocalDateTime.now().minusHours(2));
        return order;
    }

    /**
     * 배송 중인 주문 (PAYMENT_COMPLETED + SHIPPING).
     */
    public static Order aShippingOrder() {
        Order order = Order.builder()
                .auctionId(10L)
                .cardId(3L)
                .sellerId(2L)
                .buyerId(1L)
                .orderUid("ORD-001")
                .finalPrice(10000L)
                .status(OrderStatus.PAYMENT_COMPLETED)
                .deliveryStatus(DeliveryStatus.SHIPPING)
                .build();
        ReflectionTestUtils.setField(order, "id", 1L);
        ReflectionTestUtils.setField(order, "createdAt", LocalDateTime.now().minusDays(1));
        ReflectionTestUtils.setField(order, "updatedAt", LocalDateTime.now().minusHours(6));
        return order;
    }

    // ── Payment ──────────────────────────────────────────────────────

    /**
     * id=1L, orderId=1L, paymentUid="PAY-001", amount=10000L, PG_DIRECT
     */
    public static Payment aPayment(PaymentStatus status) {
        Payment payment = Payment.builder()
                .orderId(1L)
                .paymentUid("PAY-001")
                .amount(10000L)
                .paymentType(PaymentType.PG_DIRECT)
                .status(status)
                .build();
        ReflectionTestUtils.setField(payment, "id", 1L);
        ReflectionTestUtils.setField(payment, "createdAt", LocalDateTime.now().minusMinutes(10));
        return payment;
    }

    /**
     * id=1L, orderId=1L, paymentUid="PAY-001", amount=10000L, BILLING_KEY
     */
    public static Payment aBillingKeyPayment(PaymentStatus status) {
        Payment payment = Payment.builder()
                .orderId(1L)
                .paymentUid("PAY-001")
                .amount(10000L)
                .paymentType(PaymentType.BILLING_KEY)
                .status(status)
                .build();
        ReflectionTestUtils.setField(payment, "id", 1L);
        ReflectionTestUtils.setField(payment, "createdAt", LocalDateTime.now().minusMinutes(10));
        return payment;
    }

    // ── Card ─────────────────────────────────────────────────────────

    public static Series aSeries() {
        Series s = Series.builder().name("Sword & Shield").build();
        ReflectionTestUtils.setField(s, "id", 1L);
        return s;
    }

    public static PokemonSet aPokemonSet() {
        PokemonSet ps = PokemonSet.builder()
                .setId("swsh5")
                .name("Rebel Clash")
                .series(aSeries())
                .build();
        ReflectionTestUtils.setField(ps, "id", 1L);
        return ps;
    }

    /** id=3L, name="피카츄", grade=PSA_10 */
    public static Card aCard() {
        Card card = Card.builder()
                .userId(2L)
                .name("피카츄")
                .series(aSeries())
                .pokemonSet(aPokemonSet())
                .cardNumber("58")
                .rarity("Rare")
                .category(CardCategory.POKEMON)
                .grade(CardGrade.PSA_10)
                .imageUrl("https://images.pocat.io/pikachu.jpg")
                .source(CardSource.MANUAL)
                .status(CardStatus.ACTIVE)
                .build();
        ReflectionTestUtils.setField(card, "id", 3L);
        return card;
    }
}
