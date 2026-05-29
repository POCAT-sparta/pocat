package com.rocketcrew.pocat.domain.payment.service;

import com.rocketcrew.pocat.domain.order.entity.Order;
import com.rocketcrew.pocat.domain.order.enums.OrderStatus;
import com.rocketcrew.pocat.domain.order.service.OrderQueryService;
import com.rocketcrew.pocat.domain.payment.client.out.portone.PortOneClientService;
import com.rocketcrew.pocat.domain.payment.client.out.portone.PortOneStatus;
import com.rocketcrew.pocat.domain.payment.client.out.portone.dto.PortOnePaymentResponse;
import com.rocketcrew.pocat.domain.payment.dto.request.CreatePaymentRequest;
import com.rocketcrew.pocat.domain.payment.dto.response.PaymentResponse;
import com.rocketcrew.pocat.domain.payment.entity.Payment;
import com.rocketcrew.pocat.domain.payment.entity.PaymentStatus;
import com.rocketcrew.pocat.domain.payment.entity.PaymentType;
import com.rocketcrew.pocat.domain.user.entity.User;
import com.rocketcrew.pocat.domain.user.repository.UserRepository;
import com.rocketcrew.pocat.global.exception.common.ErrorCode;
import com.rocketcrew.pocat.global.exception.domain.PaymentException;
import com.rocketcrew.pocat.support.TestFixtures;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("PaymentApplicationService")
class PaymentApplicationServiceTest {

    @InjectMocks
    private PaymentApplicationService paymentApplicationService;

    @Mock private PortOneClientService portOneClientService;
    @Mock private FailureService failureService;
    @Mock private UserRepository userRepository;
    @Mock private PaymentCommandService paymentCommandService;
    @Mock private PaymentQueryService paymentQueryService;
    @Mock private OrderQueryService orderQueryService;

    // ── generatePayment ────────────────────────────────────────────────

    @Nested
    @DisplayName("generatePayment()")
    class GeneratePayment {

        @Test
        @DisplayName("성공: PAYMENT_FAILED 주문에 대해 새 PENDING 결제를 생성한다")
        void success() {
            Order order = TestFixtures.aPaymentFailedOrder();
            CreatePaymentRequest request = new CreatePaymentRequest(1L);
            Payment saved = TestFixtures.aPayment(PaymentStatus.PENDING);

            given(orderQueryService.findByOrderIdWithLock(1L)).willReturn(order);
            given(paymentCommandService.createPayment(order.getId(), PaymentType.PG_DIRECT)).willReturn(saved);

            PaymentResponse response = paymentApplicationService.generatePayment(1L, request);

            assertThat(response.status()).isEqualTo(PaymentStatus.PENDING);
            verify(paymentCommandService).createPayment(order.getId(), PaymentType.PG_DIRECT);
        }

        @Test
        @DisplayName("실패: 주문 없음 → ORDER_NOT_FOUND")
        void fail_orderNotFound() {
            given(orderQueryService.findByOrderIdWithLock(99L))
                    .willThrow(new PaymentException(ErrorCode.ORDER_NOT_FOUND));

            assertThatThrownBy(() -> paymentApplicationService.generatePayment(1L, new CreatePaymentRequest(99L)))
                    .isInstanceOf(PaymentException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.ORDER_NOT_FOUND);
        }

        @Test
        @DisplayName("실패: 구매자 불일치 → PAYMENT_BUYER_MISMATCH")
        void fail_buyerMismatch() {
            Order order = TestFixtures.aPaymentFailedOrder(); // buyerId=1L
            given(orderQueryService.findByOrderIdWithLock(1L)).willReturn(order);

            assertThatThrownBy(() -> paymentApplicationService.generatePayment(99L, new CreatePaymentRequest(1L)))
                    .isInstanceOf(PaymentException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.PAYMENT_BUYER_MISMATCH);
        }

        @Test
        @DisplayName("실패: 주문 상태가 PAYMENT_FAILED 아님 → PAYMENT_ORDER_NOT_FAILED")
        void fail_orderNotPaymentFailed() {
            Order order = TestFixtures.anOrder(OrderStatus.PAYMENT_PENDING);
            given(orderQueryService.findByOrderIdWithLock(1L)).willReturn(order);

            assertThatThrownBy(() -> paymentApplicationService.generatePayment(1L, new CreatePaymentRequest(1L)))
                    .isInstanceOf(PaymentException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.PAYMENT_ORDER_NOT_FAILED);
        }

        @Test
        @DisplayName("실패: 결제 가능 시간(1시간) 초과 → PAYMENT_WINDOW_EXPIRED")
        void fail_windowExpired() {
            Order order = TestFixtures.anExpiredPaymentFailedOrder();
            given(orderQueryService.findByOrderIdWithLock(1L)).willReturn(order);

            assertThatThrownBy(() -> paymentApplicationService.generatePayment(1L, new CreatePaymentRequest(1L)))
                    .isInstanceOf(PaymentException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.PAYMENT_WINDOW_EXPIRED);
        }

        @Test
        @DisplayName("성공: PAYMENT_FAILED 주문에 PG_DIRECT 결제를 신규 생성한다")
        void idempotent_pendingAlreadyExists() {
            Order order = TestFixtures.aPaymentFailedOrder();
            Payment existing = TestFixtures.aPayment(PaymentStatus.PENDING);

            given(orderQueryService.findByOrderIdWithLock(1L)).willReturn(order);
            given(paymentCommandService.createPayment(order.getId(), PaymentType.PG_DIRECT)).willReturn(existing);

            PaymentResponse response = paymentApplicationService.generatePayment(1L, new CreatePaymentRequest(1L));

            assertThat(response.paymentUid()).isEqualTo("PAY-001");
            verify(paymentCommandService).createPayment(eq(order.getId()), eq(PaymentType.PG_DIRECT));
        }
    }

    // ── confirmPayment ─────────────────────────────────────────────────

    @Nested
    @DisplayName("confirmPayment()")
    class ConfirmPayment {

        @Test
        @DisplayName("성공: PortOne PAID, 금액 일치 → completePayment 호출")
        void success() {
            Payment payment = TestFixtures.aPayment(PaymentStatus.PENDING);
            Order order = TestFixtures.anOrder(OrderStatus.AUTO_PAYMENT_FAILED);
            LocalDateTime paidAt = LocalDateTime.now();

            given(paymentQueryService.findPaymentByUid("PAY-001")).willReturn(payment);
            given(orderQueryService.findByOrderid(1L)).willReturn(order);
            given(paymentQueryService.findPaymentByUidWithLock("PAY-001")).willReturn(payment);
            given(portOneClientService.getPayment("PAY-001")).willReturn(
                    new PortOnePaymentResponse(PortOneStatus.PAID, 10000L, "CARD", paidAt, "", null, null, null));

            paymentApplicationService.confirmPayment(1L, "PAY-001");

            verify(paymentCommandService).completePayment(eq(payment), eq(order), eq("CARD"), eq(paidAt));
        }

        @Test
        @DisplayName("실패: 결제 없음 → PAYMENT_NOT_FOUND")
        void fail_paymentNotFound() {
            given(paymentQueryService.findPaymentByUid("UNKNOWN"))
                    .willThrow(new PaymentException(ErrorCode.PAYMENT_NOT_FOUND));

            assertThatThrownBy(() -> paymentApplicationService.confirmPayment(1L, "UNKNOWN"))
                    .isInstanceOf(PaymentException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.PAYMENT_NOT_FOUND);
        }

        @Test
        @DisplayName("실패: 구매자 불일치 → PAYMENT_BUYER_MISMATCH")
        void fail_buyerMismatch() {
            Payment payment = TestFixtures.aPayment(PaymentStatus.PENDING);
            Order order = TestFixtures.anOrder(OrderStatus.AUTO_PAYMENT_FAILED); // buyerId=1L

            given(paymentQueryService.findPaymentByUid("PAY-001")).willReturn(payment);
            given(orderQueryService.findByOrderid(1L)).willReturn(order);

            assertThatThrownBy(() -> paymentApplicationService.confirmPayment(99L, "PAY-001"))
                    .isInstanceOf(PaymentException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.PAYMENT_BUYER_MISMATCH);
        }

        @Test
        @DisplayName("멱등: 이미 COMPLETED 상태이면 portOne 조회 없이 바로 반환한다")
        void idempotent_alreadyCompleted() {
            Payment payment = TestFixtures.aPayment(PaymentStatus.COMPLETED);
            Order order = TestFixtures.anOrder(OrderStatus.PAYMENT_COMPLETED);

            given(paymentQueryService.findPaymentByUid("PAY-001")).willReturn(payment);
            given(orderQueryService.findByOrderid(1L)).willReturn(order);

            PaymentResponse response = paymentApplicationService.confirmPayment(1L, "PAY-001");

            assertThat(response.status()).isEqualTo(PaymentStatus.COMPLETED);
            verify(portOneClientService, never()).getPayment(anyString());
            verify(paymentCommandService, never()).completePayment(any(), any(), any(), any());
        }

        @Test
        @DisplayName("실패: PortOne 상태가 PAID 아님 → PAYMENT_STATUS_NOT_PAID")
        void fail_portOneNotPaid() {
            Payment payment = TestFixtures.aPayment(PaymentStatus.PENDING);
            Order order = TestFixtures.anOrder(OrderStatus.AUTO_PAYMENT_FAILED);

            given(paymentQueryService.findPaymentByUid("PAY-001")).willReturn(payment);
            given(orderQueryService.findByOrderid(1L)).willReturn(order);
            given(paymentQueryService.findPaymentByUidWithLock("PAY-001")).willReturn(payment);
            given(portOneClientService.getPayment("PAY-001")).willReturn(
                    new PortOnePaymentResponse(PortOneStatus.FAILED, 15000L, "CARD", LocalDateTime.now(), "", null, null, null));

            assertThatThrownBy(() -> paymentApplicationService.confirmPayment(1L, "PAY-001"))
                    .isInstanceOf(PaymentException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.PAYMENT_STATUS_NOT_PAID);
        }

        @Test
        @DisplayName("실패: 금액 불일치(부족) → PAYMENT_AMOUNT_MISMATCH")
        void fail_amountMismatch() {
            Payment payment = TestFixtures.aPayment(PaymentStatus.PENDING); // amount=10000L
            Order order = TestFixtures.anOrder(OrderStatus.AUTO_PAYMENT_FAILED);

            given(paymentQueryService.findPaymentByUid("PAY-001")).willReturn(payment);
            given(orderQueryService.findByOrderid(1L)).willReturn(order);
            given(paymentQueryService.findPaymentByUidWithLock("PAY-001")).willReturn(payment);
            given(portOneClientService.getPayment("PAY-001")).willReturn(
                    new PortOnePaymentResponse(PortOneStatus.PAID, 15000L, "CARD", LocalDateTime.now(), "", null, null, null));


            assertThatThrownBy(() -> paymentApplicationService.confirmPayment(1L, "PAY-001"))
                    .isInstanceOf(PaymentException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.PAYMENT_AMOUNT_MISMATCH);
        }

        @Test
        @DisplayName("실패: 금액 불일치(초과) → PAYMENT_AMOUNT_MISMATCH")
        void fail_amountMismatch_over() {
            Payment payment = TestFixtures.aPayment(PaymentStatus.PENDING); // amount=10000L
            Order order = TestFixtures.anOrder(OrderStatus.AUTO_PAYMENT_FAILED);

            given(paymentQueryService.findPaymentByUid("PAY-001")).willReturn(payment);
            given(orderQueryService.findByOrderid(1L)).willReturn(order);
            given(paymentQueryService.findPaymentByUidWithLock("PAY-001")).willReturn(payment);
            given(portOneClientService.getPayment("PAY-001")).willReturn(
                    new PortOnePaymentResponse(PortOneStatus.PAID, 15000L, "CARD", LocalDateTime.now(), "", null, null, null));

            assertThatThrownBy(() -> paymentApplicationService.confirmPayment(1L, "PAY-001"))
                    .isInstanceOf(PaymentException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.PAYMENT_AMOUNT_MISMATCH);
        }
    }

    // ── autoPayment ────────────────────────────────────────────────────

    @Nested
    @DisplayName("autoPayment()")
    class AutoPayment {

        @Test
        @DisplayName("성공: 빌링키 결제 PAID → completePayment 호출")
        void success() {
            Order order = TestFixtures.anOrder(OrderStatus.PAYMENT_PENDING);
            User user = TestFixtures.aUserWithBillingKey();
            Payment payment = TestFixtures.aBillingKeyPayment(PaymentStatus.PENDING);
            LocalDateTime paidAt = LocalDateTime.now();

            given(orderQueryService.findByOrderid(1L)).willReturn(order);
            given(userRepository.findById(1L)).willReturn(Optional.of(user));
            given(paymentCommandService.createPayment(order.getId(), PaymentType.BILLING_KEY)).willReturn(payment);
            given(portOneClientService.attemptBillingKeyPayment(anyString(), eq("bkey-001"), eq(10000L)))
                    .willReturn(new PortOnePaymentResponse(PortOneStatus.PAID, 10000L, "BILLING_KEY", paidAt, null, null, null, null));

            paymentApplicationService.autoPayment("1");

            verify(paymentCommandService).completePayment(eq(payment), eq(order), eq("BILLING_KEY"), eq(paidAt));
        }

        @Test
        @DisplayName("멱등: 이미 PAYMENT_COMPLETED 상태이면 기존 결제를 반환한다")
        void idempotent_alreadyCompleted() {
            Order order = TestFixtures.anOrder(OrderStatus.PAYMENT_COMPLETED);

            given(orderQueryService.findByOrderid(1L)).willReturn(order);
            given(paymentQueryService.findByOrderId(1L)).willReturn(
                    PaymentResponse.from(TestFixtures.aBillingKeyPayment(PaymentStatus.COMPLETED)));

            PaymentResponse response = paymentApplicationService.autoPayment("1");

            assertThat(response.status()).isEqualTo(PaymentStatus.COMPLETED);
            verify(portOneClientService, never()).attemptBillingKeyPayment(anyString(), anyString(), anyLong());
        }

        @Test
        @DisplayName("실패: 빌링키 없음 → BILLING_KEY_NOT_FOUND")
        void fail_billingKeyNotFound() {
            Order order = TestFixtures.anOrder(OrderStatus.PAYMENT_PENDING);
            User user = TestFixtures.aUser(); // billingKey=null

            given(orderQueryService.findByOrderid(1L)).willReturn(order);
            given(userRepository.findById(1L)).willReturn(Optional.of(user));

            assertThatThrownBy(() -> paymentApplicationService.autoPayment("1"))
                    .isInstanceOf(PaymentException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.BILLING_KEY_NOT_FOUND);
        }

        @Test
        @DisplayName("실패: PortOne 결제 실패 → persistBillingKeyFailure 호출 후 PAYMENT_STATUS_NOT_PAID")
        void fail_portOnePaymentFailed() {
            Order order = TestFixtures.anOrder(OrderStatus.PAYMENT_PENDING);
            User user = TestFixtures.aUserWithBillingKey();
            Payment payment = TestFixtures.aBillingKeyPayment(PaymentStatus.PENDING);

            given(orderQueryService.findByOrderid(1L)).willReturn(order);
            given(userRepository.findById(1L)).willReturn(Optional.of(user));
            given(paymentCommandService.createPayment(order.getId(), PaymentType.BILLING_KEY)).willReturn(payment);
            given(portOneClientService.attemptBillingKeyPayment(anyString(), anyString(), anyLong()))
                    .willReturn(new PortOnePaymentResponse(PortOneStatus.FAILED, 10000L, null, null, null, null, null, null));

            assertThatThrownBy(() -> paymentApplicationService.autoPayment("1"))
                    .isInstanceOf(PaymentException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.PAYMENT_STATUS_NOT_PAID);

            verify(failureService).persistBillingKeyFailure(payment.getId(), order.getId());
        }
    }
}
