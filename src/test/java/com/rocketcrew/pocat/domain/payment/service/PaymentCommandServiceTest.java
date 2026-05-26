package com.rocketcrew.pocat.domain.payment.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rocketcrew.pocat.domain.order.entity.Order;
import com.rocketcrew.pocat.domain.order.enums.OrderStatus;
import com.rocketcrew.pocat.domain.order.repository.OrderRepository;
import com.rocketcrew.pocat.domain.payment.client.PortOneClient;
import com.rocketcrew.pocat.domain.payment.client.PortOnePaymentResponse;
import com.rocketcrew.pocat.domain.payment.dto.request.CreatePaymentRequest;
import com.rocketcrew.pocat.domain.payment.dto.response.PaymentResponse;
import com.rocketcrew.pocat.domain.payment.entity.Payment;
import com.rocketcrew.pocat.domain.payment.entity.PaymentStatus;
import com.rocketcrew.pocat.domain.payment.entity.PaymentType;
import com.rocketcrew.pocat.domain.payment.repository.PaymentRepository;
import com.rocketcrew.pocat.domain.settlement.service.SettlementCommandService;
import com.rocketcrew.pocat.domain.user.entity.User;
import com.rocketcrew.pocat.domain.user.repository.UserRepository;
import com.rocketcrew.pocat.global.exception.common.ErrorCode;
import com.rocketcrew.pocat.global.exception.domain.OrderException;
import com.rocketcrew.pocat.global.exception.domain.PaymentException;
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

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willDoNothing;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("PaymentCommandService")
class PaymentCommandServiceTest {

    @InjectMocks
    private PaymentCommandService paymentCommandService;

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private ObjectMapper objectMapper;

    @Mock
    private PaymentFailureService paymentFailureService;

    @Mock
    private SettlementCommandService settlementCommandService;

    @Mock
    private UserRepository userRepository;

    @Mock
    private PortOneClient portOneClient;

    // ── createPayment ──────────────────────────────────────────────────

    @Nested
    @DisplayName("createPayment()")
    class CreatePayment {

        @Test
        @DisplayName("성공: PAYMENT_FAILED 주문에 대해 새 PENDING 결제를 생성한다")
        void success() {
            Order order = TestFixtures.aPaymentFailedOrder();
            CreatePaymentRequest request = new CreatePaymentRequest(1L);

            given(orderRepository.findById(1L)).willReturn(Optional.of(order));
            given(paymentRepository.findByOrderIdAndStatus(1L, PaymentStatus.PENDING))
                    .willReturn(Optional.empty());

            Payment saved = TestFixtures.aPayment(PaymentStatus.PENDING);
            given(paymentRepository.save(any(Payment.class))).willReturn(saved);

            PaymentResponse response = paymentCommandService.createPayment(1L, request);

            assertThat(response).isNotNull();
            assertThat(response.status()).isEqualTo(PaymentStatus.PENDING);
            assertThat(response.amount()).isEqualTo(10000L);
        }

        @Test
        @DisplayName("실패: 주문 없음 → ORDER_NOT_FOUND")
        void fail_orderNotFound() {
            CreatePaymentRequest request = new CreatePaymentRequest(99L);
            given(orderRepository.findById(99L)).willReturn(Optional.empty());

            assertThatThrownBy(() -> paymentCommandService.createPayment(1L, request))
                    .isInstanceOf(OrderException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.ORDER_NOT_FOUND);
        }

        @Test
        @DisplayName("실패: 구매자 불일치 → PAYMENT_BUYER_MISMATCH")
        void fail_buyerMismatch() {
            Order order = TestFixtures.aPaymentFailedOrder(); // buyerId=1L
            CreatePaymentRequest request = new CreatePaymentRequest(1L);
            given(orderRepository.findById(1L)).willReturn(Optional.of(order));

            assertThatThrownBy(() -> paymentCommandService.createPayment(99L, request))
                    .isInstanceOf(PaymentException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.PAYMENT_BUYER_MISMATCH);
        }

        @Test
        @DisplayName("실패: 주문 상태가 PAYMENT_FAILED 아님 → PAYMENT_ORDER_NOT_FAILED")
        void fail_orderNotPaymentFailed() {
            Order order = TestFixtures.anOrder(OrderStatus.PAYMENT_PENDING);
            CreatePaymentRequest request = new CreatePaymentRequest(1L);
            given(orderRepository.findById(1L)).willReturn(Optional.of(order));

            assertThatThrownBy(() -> paymentCommandService.createPayment(1L, request))
                    .isInstanceOf(PaymentException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.PAYMENT_ORDER_NOT_FAILED);
        }

        @Test
        @DisplayName("실패: 결제 가능 시간(1시간) 초과 → PAYMENT_WINDOW_EXPIRED")
        void fail_windowExpired() {
            Order order = TestFixtures.anExpiredPaymentFailedOrder();
            CreatePaymentRequest request = new CreatePaymentRequest(1L);
            given(orderRepository.findById(1L)).willReturn(Optional.of(order));

            assertThatThrownBy(() -> paymentCommandService.createPayment(1L, request))
                    .isInstanceOf(PaymentException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.PAYMENT_WINDOW_EXPIRED);
        }

        @Test
        @DisplayName("멱등: 이미 PENDING 결제가 존재하면 기존 paymentUid를 반환한다")
        void idempotent_pendingAlreadyExists() {
            Order order = TestFixtures.aPaymentFailedOrder();
            Payment existing = TestFixtures.aPayment(PaymentStatus.PENDING);
            CreatePaymentRequest request = new CreatePaymentRequest(1L);

            given(orderRepository.findById(1L)).willReturn(Optional.of(order));
            given(paymentRepository.findByOrderIdAndStatus(1L, PaymentStatus.PENDING))
                    .willReturn(Optional.of(existing));

            PaymentResponse response = paymentCommandService.createPayment(1L, request);

            assertThat(response.paymentUid()).isEqualTo("PAY-001");
            verify(paymentRepository, never()).save(any());
        }
    }

    // ── confirmPayment ─────────────────────────────────────────────────

    @Nested
    @DisplayName("confirmPayment()")
    class ConfirmPayment {

        @Test
        @DisplayName("성공: PortOne PAID 상태이고 금액 일치 시 결제 완료 처리한다")
        void success() {
            Payment payment = TestFixtures.aPayment(PaymentStatus.PENDING);
            Order order = TestFixtures.anOrder(OrderStatus.PAYMENT_FAILED);

            given(paymentRepository.findByPaymentUid("PAY-001")).willReturn(Optional.of(payment));
            given(orderRepository.findById(1L)).willReturn(Optional.of(order));
            given(paymentRepository.findByPaymentUidWithLock("PAY-001")).willReturn(Optional.of(payment));

            PortOnePaymentResponse portOneResponse = new PortOnePaymentResponse(
                    "PAID", 10000L, "CARD", LocalDateTime.now());
            given(portOneClient.getPayment("PAY-001")).willReturn(portOneResponse);
            willDoNothing().given(settlementCommandService).createSettlement(anyString());

            PaymentResponse response = paymentCommandService.confirmPayment(1L, "PAY-001");

            assertThat(response).isNotNull();
            assertThat(response.status()).isEqualTo(PaymentStatus.COMPLETED);
        }

        @Test
        @DisplayName("실패: 결제 없음 → PAYMENT_NOT_FOUND")
        void fail_paymentNotFound() {
            given(paymentRepository.findByPaymentUid("UNKNOWN")).willReturn(Optional.empty());

            assertThatThrownBy(() -> paymentCommandService.confirmPayment(1L, "UNKNOWN"))
                    .isInstanceOf(PaymentException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.PAYMENT_NOT_FOUND);
        }

        @Test
        @DisplayName("실패: 구매자 불일치 → PAYMENT_BUYER_MISMATCH")
        void fail_buyerMismatch() {
            Payment payment = TestFixtures.aPayment(PaymentStatus.PENDING);
            Order order = TestFixtures.anOrder(OrderStatus.PAYMENT_FAILED); // buyerId=1L

            given(paymentRepository.findByPaymentUid("PAY-001")).willReturn(Optional.of(payment));
            given(orderRepository.findById(1L)).willReturn(Optional.of(order));

            assertThatThrownBy(() -> paymentCommandService.confirmPayment(99L, "PAY-001"))
                    .isInstanceOf(PaymentException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.PAYMENT_BUYER_MISMATCH);
        }

        @Test
        @DisplayName("멱등: 이미 COMPLETED 상태이면 바로 반환한다")
        void idempotent_alreadyCompleted() {
            Payment payment = TestFixtures.aPayment(PaymentStatus.COMPLETED);
            Order order = TestFixtures.anOrder(OrderStatus.PAYMENT_COMPLETED);

            given(paymentRepository.findByPaymentUid("PAY-001")).willReturn(Optional.of(payment));
            given(orderRepository.findById(1L)).willReturn(Optional.of(order));

            PaymentResponse response = paymentCommandService.confirmPayment(1L, "PAY-001");

            assertThat(response.status()).isEqualTo(PaymentStatus.COMPLETED);
            verify(portOneClient, never()).getPayment(anyString());
            verify(settlementCommandService, never()).createSettlement(anyString());
        }

        @Test
        @DisplayName("실패: PortOne 상태가 PAID 아님 → PAYMENT_STATUS_NOT_PAID")
        void fail_portOneNotPaid() {
            Payment payment = TestFixtures.aPayment(PaymentStatus.PENDING);
            Order order = TestFixtures.anOrder(OrderStatus.PAYMENT_FAILED);

            given(paymentRepository.findByPaymentUid("PAY-001")).willReturn(Optional.of(payment));
            given(orderRepository.findById(1L)).willReturn(Optional.of(order));
            given(paymentRepository.findByPaymentUidWithLock("PAY-001")).willReturn(Optional.of(payment));

            PortOnePaymentResponse portOneResponse = new PortOnePaymentResponse(
                    "FAILED", 10000L, null, null);
            given(portOneClient.getPayment("PAY-001")).willReturn(portOneResponse);
            willDoNothing().given(paymentFailureService).markFailed(anyLong());

            assertThatThrownBy(() -> paymentCommandService.confirmPayment(1L, "PAY-001"))
                    .isInstanceOf(PaymentException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.PAYMENT_STATUS_NOT_PAID);
        }

        @Test
        @DisplayName("실패: 금액 불일치 → PAYMENT_AMOUNT_MISMATCH")
        void fail_amountMismatch() {
            Payment payment = TestFixtures.aPayment(PaymentStatus.PENDING); // amount=10000L
            Order order = TestFixtures.anOrder(OrderStatus.PAYMENT_FAILED);

            given(paymentRepository.findByPaymentUid("PAY-001")).willReturn(Optional.of(payment));
            given(orderRepository.findById(1L)).willReturn(Optional.of(order));
            given(paymentRepository.findByPaymentUidWithLock("PAY-001")).willReturn(Optional.of(payment));

            PortOnePaymentResponse portOneResponse = new PortOnePaymentResponse(
                    "PAID", 5000L, "CARD", LocalDateTime.now()); // 금액 불일치
            given(portOneClient.getPayment("PAY-001")).willReturn(portOneResponse);
            willDoNothing().given(paymentFailureService).markFailed(anyLong());

            assertThatThrownBy(() -> paymentCommandService.confirmPayment(1L, "PAY-001"))
                    .isInstanceOf(PaymentException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.PAYMENT_AMOUNT_MISMATCH);
        }

        @Test
        @DisplayName("실패: PortOne 결제금액이 주문금액보다 큰 경우 → PAYMENT_AMOUNT_MISMATCH")
        void fail_amountMismatch_over() {
            // 프로덕션 코드는 payment.getAmount().equals(portOneClientPayment.amount()) 로 정확히 일치 여부만 검사 (!=)
            // 따라서 초과 금액(15000L > 10000L)도 동일하게 PAYMENT_AMOUNT_MISMATCH 를 발생시켜야 한다.
            Payment payment = TestFixtures.aPayment(PaymentStatus.PENDING); // amount=10000L
            Order order = TestFixtures.anOrder(OrderStatus.PAYMENT_FAILED);

            given(paymentRepository.findByPaymentUid("PAY-001")).willReturn(Optional.of(payment));
            given(orderRepository.findById(1L)).willReturn(Optional.of(order));
            given(paymentRepository.findByPaymentUidWithLock("PAY-001")).willReturn(Optional.of(payment));

            PortOnePaymentResponse portOneResponse = new PortOnePaymentResponse(
                    "PAID", 15000L, "CARD", LocalDateTime.now()); // 주문금액(10000L)보다 큰 금액
            given(portOneClient.getPayment("PAY-001")).willReturn(portOneResponse);
            willDoNothing().given(paymentFailureService).markFailed(anyLong());

            assertThatThrownBy(() -> paymentCommandService.confirmPayment(1L, "PAY-001"))
                    .isInstanceOf(PaymentException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.PAYMENT_AMOUNT_MISMATCH);
        }
    }

    // ── attemptBillingKeyPayment ───────────────────────────────────────

    @Nested
    @DisplayName("attemptBillingKeyPayment()")
    class AttemptBillingKeyPayment {

        @Test
        @DisplayName("성공: 빌링키 결제가 PAID 로 완료된다")
        void success() {
            Order order = TestFixtures.anOrder(OrderStatus.PAYMENT_PENDING);
            User user = TestFixtures.aUserWithBillingKey();
            Payment payment = TestFixtures.aBillingKeyPayment(PaymentStatus.PENDING);

            given(orderRepository.findById(1L)).willReturn(Optional.of(order));
            given(userRepository.findById(1L)).willReturn(Optional.of(user));
            given(paymentRepository.findByOrderIdAndStatus(1L, PaymentStatus.PENDING))
                    .willReturn(Optional.of(payment));

            PortOnePaymentResponse portOneResponse = new PortOnePaymentResponse(
                    "PAID", 10000L, "BILLING_KEY", LocalDateTime.now());
            given(portOneClient.attemptBillingKeyPayment(anyString(), anyString(), anyLong()))
                    .willReturn(portOneResponse);
            willDoNothing().given(settlementCommandService).createSettlement(anyString());

            PaymentResponse response = paymentCommandService.attemptBillingKeyPayment(1L);

            assertThat(response).isNotNull();
            assertThat(response.status()).isEqualTo(PaymentStatus.COMPLETED);
        }

        @Test
        @DisplayName("멱등: 이미 PAYMENT_COMPLETED 상태이면 기존 결제를 반환한다")
        void idempotent_alreadyCompleted() {
            Order order = TestFixtures.anOrder(OrderStatus.PAYMENT_COMPLETED);
            Payment payment = TestFixtures.aBillingKeyPayment(PaymentStatus.COMPLETED);

            given(orderRepository.findById(1L)).willReturn(Optional.of(order));
            given(paymentRepository.findByOrderId(1L)).willReturn(Optional.of(payment));

            PaymentResponse response = paymentCommandService.attemptBillingKeyPayment(1L);

            assertThat(response.status()).isEqualTo(PaymentStatus.COMPLETED);
            verify(portOneClient, never()).attemptBillingKeyPayment(anyString(), anyString(), anyLong());
        }

        @Test
        @DisplayName("실패: 빌링키 없음 → BILLING_KEY_NOT_FOUND")
        void fail_billingKeyNotFound() {
            Order order = TestFixtures.anOrder(OrderStatus.PAYMENT_PENDING);
            User user = TestFixtures.aUser(); // billingKey=null

            given(orderRepository.findById(1L)).willReturn(Optional.of(order));
            given(userRepository.findById(1L)).willReturn(Optional.of(user));

            assertThatThrownBy(() -> paymentCommandService.attemptBillingKeyPayment(1L))
                    .isInstanceOf(PaymentException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.BILLING_KEY_NOT_FOUND);
        }

        @Test
        @DisplayName("실패: PortOne 결제 실패 시 FAILED 상태를 반환한다")
        void fail_portOnePaymentFailed() {
            Order order = TestFixtures.anOrder(OrderStatus.PAYMENT_PENDING);
            User user = TestFixtures.aUserWithBillingKey();
            Payment payment = TestFixtures.aBillingKeyPayment(PaymentStatus.PENDING);

            given(orderRepository.findById(1L)).willReturn(Optional.of(order));
            given(userRepository.findById(1L)).willReturn(Optional.of(user));
            given(paymentRepository.findByOrderIdAndStatus(1L, PaymentStatus.PENDING))
                    .willReturn(Optional.of(payment));

            PortOnePaymentResponse failResponse = new PortOnePaymentResponse(
                    "FAILED", 10000L, null, null);
            given(portOneClient.attemptBillingKeyPayment(anyString(), anyString(), anyLong()))
                    .willReturn(failResponse);
            willDoNothing().given(paymentFailureService).markFailed(anyLong());

            PaymentResponse response = paymentCommandService.attemptBillingKeyPayment(1L);

            // markFailed 호출 후 현재 payment 상태(PENDING) 반환 (상태는 markFailed가 별도 트랜잭션에서 변경)
            verify(paymentFailureService).markFailed(1L);
            assertThat(response).isNotNull();
        }
    }

    // ── handleWebhook ──────────────────────────────────────────────────

    @Nested
    @DisplayName("handleWebhook()")
    class HandleWebhook {

        @Test
        @DisplayName("실패: rawBody 가 null 이면 WEBHOOK_EMPTY_BODY 예외 발생")
        void fail_nullBody() {
            assertThatThrownBy(() -> paymentCommandService.handleWebhook("sig", null))
                    .isInstanceOf(PaymentException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.WEBHOOK_EMPTY_BODY);
        }

        @Test
        @DisplayName("실패: rawBody 가 빈 배열이면 WEBHOOK_EMPTY_BODY 예외 발생")
        void fail_emptyBody() {
            assertThatThrownBy(() -> paymentCommandService.handleWebhook("sig", new byte[0]))
                    .isInstanceOf(PaymentException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.WEBHOOK_EMPTY_BODY);
        }

        @Test
        @DisplayName("실패: 유효한 JSON body이지만 PortOne 미연동 → PORTONE_NOT_INTEGRATED")
        void fail_portoneNotIntegrated() throws Exception {
            byte[] body = "{}".getBytes();
            given(objectMapper.readValue(eq(body), eq(
                    com.rocketcrew.pocat.domain.payment.dto.request.WebhookRequest.class)))
                    .willReturn(mock(com.rocketcrew.pocat.domain.payment.dto.request.WebhookRequest.class));

            assertThatThrownBy(() -> paymentCommandService.handleWebhook("sig", body))
                    .isInstanceOf(PaymentException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.PORTONE_NOT_INTEGRATED);
        }
    }
}
