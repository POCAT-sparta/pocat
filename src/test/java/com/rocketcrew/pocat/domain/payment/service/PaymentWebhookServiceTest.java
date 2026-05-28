package com.rocketcrew.pocat.domain.payment.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rocketcrew.pocat.domain.order.entity.Order;
import com.rocketcrew.pocat.domain.order.enums.OrderStatus;
import com.rocketcrew.pocat.domain.order.service.OrderQueryService;
import com.rocketcrew.pocat.domain.payment.client.PortOneClient;
import com.rocketcrew.pocat.domain.payment.client.PortOnePaymentResponse;
import com.rocketcrew.pocat.domain.payment.client.PortOneSignatureVerifier;
import com.rocketcrew.pocat.domain.payment.dto.request.WebhookRequest;
import com.rocketcrew.pocat.domain.payment.entity.Payment;
import com.rocketcrew.pocat.domain.payment.entity.PaymentStatus;
import com.rocketcrew.pocat.domain.payment.repository.PaymentRepository;
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

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;

@ExtendWith(MockitoExtension.class)
@DisplayName("PaymentWebhookService")
class PaymentWebhookServiceTest {

    @InjectMocks
    private PaymentWebhookService paymentWebhookService;

    @Mock private PaymentRepository paymentRepository;
    @Mock private ObjectMapper objectMapper;
    @Mock private FailureService failureService;
    @Mock private PaymentCommandService paymentCommandService;
    @Mock private PortOneClient portOneClient;
    @Mock private PortOneSignatureVerifier portOneSignatureVerifier;
    @Mock private OrderQueryService orderQueryService;

    // ── handleWebhook ──────────────────────────────────────────────────

    @Nested
    @DisplayName("handleWebhook()")
    class HandleWebhook {

        @Test
        @DisplayName("실패: rawBody가 null → WEBHOOK_EMPTY_BODY")
        void fail_nullBody() {
            assertThatThrownBy(() -> paymentWebhookService.handleWebhook("sig", null))
                    .isInstanceOf(PaymentException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.WEBHOOK_EMPTY_BODY);
        }

        @Test
        @DisplayName("실패: rawBody가 빈 배열 → WEBHOOK_EMPTY_BODY")
        void fail_emptyBody() {
            assertThatThrownBy(() -> paymentWebhookService.handleWebhook("sig", new byte[0]))
                    .isInstanceOf(PaymentException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.WEBHOOK_EMPTY_BODY);
        }

        @Test
        @DisplayName("실패: 서명 검증 실패 → WEBHOOK_SIGNATURE_INVALID")
        void fail_invalidSignature() {
            byte[] body = "{}".getBytes();
            given(portOneSignatureVerifier.verify("bad-sig", body)).willReturn(false);

            assertThatThrownBy(() -> paymentWebhookService.handleWebhook("bad-sig", body))
                    .isInstanceOf(PaymentException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.WEBHOOK_SIGNATURE_INVALID);
        }

        @Test
        @DisplayName("성공: PAID 이벤트, 금액 일치 → completePayment 호출")
        void success_paid() throws Exception {
            byte[] body = validBody();
            Payment payment = TestFixtures.aPayment(PaymentStatus.PENDING);
            Order order = TestFixtures.anOrder(OrderStatus.PAYMENT_PENDING);
            LocalDateTime paidAt = LocalDateTime.now();

            given(portOneSignatureVerifier.verify("valid-sig", body)).willReturn(true);
            given(objectMapper.readValue(body, WebhookRequest.class)).willReturn(paidWebhookRequest());
            given(paymentRepository.findByPaymentUidWithLock("PAY-001")).willReturn(Optional.of(payment));
            given(orderQueryService.findByOrderid(1L)).willReturn(order);
            given(portOneClient.getPayment("PAY-001")).willReturn(
                    new PortOnePaymentResponse("PAID", 10000L, "CARD", paidAt));

            paymentWebhookService.handleWebhook("valid-sig", body);

            verify(paymentCommandService).completePayment(eq(payment), eq(order), eq("CARD"), eq(paidAt));
        }

        @Test
        @DisplayName("무시: 결제 레코드가 DB에 없으면 조용히 넘긴다")
        void ignore_paymentNotFound() throws Exception {
            byte[] body = validBody();

            given(portOneSignatureVerifier.verify("valid-sig", body)).willReturn(true);
            given(objectMapper.readValue(body, WebhookRequest.class)).willReturn(paidWebhookRequest());
            given(paymentRepository.findByPaymentUidWithLock("PAY-001")).willReturn(Optional.empty());

            paymentWebhookService.handleWebhook("valid-sig", body);

            verify(paymentCommandService, never()).completePayment(any(), any(), any(), any());
        }

        @Test
        @DisplayName("멱등: 이미 완료된 결제이면 재처리하지 않는다")
        void idempotent_alreadyFinalized() throws Exception {
            byte[] body = validBody();
            Payment payment = TestFixtures.aPayment(PaymentStatus.COMPLETED);

            given(portOneSignatureVerifier.verify("valid-sig", body)).willReturn(true);
            given(objectMapper.readValue(body, WebhookRequest.class)).willReturn(paidWebhookRequest());
            given(paymentRepository.findByPaymentUidWithLock("PAY-001")).willReturn(Optional.of(payment));

            paymentWebhookService.handleWebhook("valid-sig", body);

            verify(paymentCommandService, never()).completePayment(any(), any(), any(), any());
        }

        @Test
        @DisplayName("실패: PAID이지만 금액 불일치 → markFailed + cancelExpiry + PAYMENT_AMOUNT_MISMATCH")
        void fail_amountMismatch() throws Exception {
            byte[] body = validBody();
            Payment payment = TestFixtures.aPayment(PaymentStatus.PENDING); // amount=10000L
            Order order = TestFixtures.anOrder(OrderStatus.PAYMENT_PENDING);

            given(portOneSignatureVerifier.verify("valid-sig", body)).willReturn(true);
            given(objectMapper.readValue(body, WebhookRequest.class)).willReturn(paidWebhookRequest());
            given(paymentRepository.findByPaymentUidWithLock("PAY-001")).willReturn(Optional.of(payment));
            given(orderQueryService.findByOrderid(1L)).willReturn(order);
            given(portOneClient.getPayment("PAY-001")).willReturn(
                    new PortOnePaymentResponse("PAID", 5000L, "CARD", LocalDateTime.now())); // 금액 불일치

            assertThatThrownBy(() -> paymentWebhookService.handleWebhook("valid-sig", body))
                    .isInstanceOf(PaymentException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.PAYMENT_AMOUNT_MISMATCH);

            verify(failureService).markFailed(eq(1L), anyString());
            verify(failureService).cancelExpiry(1L);
            verify(paymentCommandService, never()).completePayment(any(), any(), any(), any());
        }

        @Test
        @DisplayName("성공: PAID 아닌 상태 → markFailed + cancelExpiry")
        void success_otherStatus() throws Exception {
            byte[] body = validBody();
            Payment payment = TestFixtures.aPayment(PaymentStatus.PENDING);

            given(portOneSignatureVerifier.verify("valid-sig", body)).willReturn(true);
            given(objectMapper.readValue(body, WebhookRequest.class)).willReturn(failedWebhookRequest());
            given(paymentRepository.findByPaymentUidWithLock("PAY-001")).willReturn(Optional.of(payment));

            paymentWebhookService.handleWebhook("valid-sig", body);

            verify(failureService).markFailed(eq(1L), anyString());
            verify(failureService).cancelExpiry(1L);
            verify(paymentCommandService, never()).completePayment(any(), any(), any(), any());
        }

        // ── 헬퍼 ─────────────────────────────────────────────────────

        private byte[] validBody() {
            return "{\"type\":\"Transaction.Paid\"}".getBytes();
        }

        private WebhookRequest paidWebhookRequest() {
            return new WebhookRequest(
                    "Transaction.Paid", "2024-01-01T00:00:00",
                    new WebhookRequest.WebhookData(
                            "PAY-001", null, null,
                            new WebhookRequest.WebhookAmount(10000L), "PAID"));
        }

        private WebhookRequest failedWebhookRequest() {
            return new WebhookRequest(
                    "Transaction.Failed", "2024-01-01T00:00:00",
                    new WebhookRequest.WebhookData(
                            "PAY-001", null, null,
                            new WebhookRequest.WebhookAmount(10000L), "FAILED"));
        }
    }
}
