package com.rocketcrew.pocat.domain.payment.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rocketcrew.pocat.domain.order.service.SetExpireService;
import com.rocketcrew.pocat.domain.payment.client.in.portone.PortOneWebhookService;
import com.rocketcrew.pocat.domain.payment.client.out.portone.PortOneClientService;
import com.rocketcrew.pocat.domain.payment.client.in.portone.WebhookEventCommandService;
import com.rocketcrew.pocat.domain.payment.client.out.portone.PortOneStatus;
import com.rocketcrew.pocat.domain.payment.client.out.portone.dto.PortOnePaymentResponse;
import com.rocketcrew.pocat.domain.payment.client.out.portone.PortOneSignatureVerifier;
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

import com.rocketcrew.pocat.domain.payment.enums.PaymentErrorReason;

@ExtendWith(MockitoExtension.class)
@DisplayName("PaymentWebhookService")
class PortOneWebhookServiceTest {

    @InjectMocks
    private PortOneWebhookService portOneWebhookService;

    @Mock private PaymentRepository paymentRepository;
    @Mock private ObjectMapper objectMapper;
    @Mock private FailureService failureService;
    @Mock private PaymentCommandService paymentCommandService;
    @Mock private PortOneClientService portOneClientService;
    @Mock private PortOneSignatureVerifier portOneSignatureVerifier;
    @Mock private WebhookEventCommandService webhookEventCommandService;
    @Mock private SetExpireService setExpireService;

    // ── handleWebhook ──────────────────────────────────────────────────

    @Nested
    @DisplayName("handleWebhook()")
    class HandleWebhook {

        @Test
        @DisplayName("실패: rawBody가 null → WEBHOOK_EMPTY_BODY")
        void fail_nullBody() {
            assertThatThrownBy(() -> portOneWebhookService.handleWebhook("sig", null))
                    .isInstanceOf(PaymentException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.WEBHOOK_EMPTY_BODY);
        }

        @Test
        @DisplayName("실패: rawBody가 빈 배열 → WEBHOOK_EMPTY_BODY")
        void fail_emptyBody() {
            assertThatThrownBy(() -> portOneWebhookService.handleWebhook("sig", new byte[0]))
                    .isInstanceOf(PaymentException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.WEBHOOK_EMPTY_BODY);
        }

        @Test
        @DisplayName("실패: 서명 검증 실패 → WEBHOOK_SIGNATURE_INVALID")
        void fail_invalidSignature() {
            byte[] body = "{}".getBytes();
            given(portOneSignatureVerifier.verify("bad-sig", body)).willReturn(false);

            assertThatThrownBy(() -> portOneWebhookService.handleWebhook("bad-sig", body))
                    .isInstanceOf(PaymentException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.WEBHOOK_SIGNATURE_INVALID);
        }

        @Test
        @DisplayName("성공: PAID 이벤트, 금액 일치 → completePayment 호출")
        void success_paid() throws Exception {
            byte[] body = validBody();
            Payment payment = TestFixtures.aPayment(PaymentStatus.PENDING);
            LocalDateTime paidAt = LocalDateTime.now();

            given(portOneSignatureVerifier.verify("valid-sig", body)).willReturn(true);
            given(objectMapper.readValue(body, WebhookRequest.class)).willReturn(paidWebhookRequest());
            given(webhookEventCommandService.saveReceivedOrGet(anyString(), anyString(), anyString()))
                    .willReturn(TestFixtures.aWebhookEvent());
            given(paymentRepository.findByPaymentUidWithLock("PAY-001")).willReturn(Optional.of(payment));
            given(portOneClientService.getPayment("PAY-001")).willReturn(
                    new PortOnePaymentResponse(PortOneStatus.PAID, 10000L, "CARD", paidAt, "", null, null, null));

            portOneWebhookService.handleWebhook("valid-sig", body);

            verify(paymentCommandService).completePayment(eq(payment.getId()), eq(payment.getOrderId()), eq("CARD"), eq(paidAt));
        }

        @Test
        @DisplayName("무시: 결제 레코드가 DB에 없으면 조용히 넘긴다")
        void ignore_paymentNotFound() throws Exception {
            byte[] body = validBody();

            given(portOneSignatureVerifier.verify("valid-sig", body)).willReturn(true);
            given(objectMapper.readValue(body, WebhookRequest.class)).willReturn(paidWebhookRequest());
            given(webhookEventCommandService.saveReceivedOrGet(anyString(), anyString(), anyString()))
                    .willReturn(TestFixtures.aWebhookEvent());
            given(paymentRepository.findByPaymentUidWithLock("PAY-001")).willReturn(Optional.empty());

            portOneWebhookService.handleWebhook("valid-sig", body);

            verify(paymentCommandService, never()).completePayment(any(), any(), any(), any());
        }

        @Test
        @DisplayName("멱등: 이미 완료된 결제이면 재처리하지 않는다")
        void idempotent_alreadyFinalized() throws Exception {
            byte[] body = validBody();
            Payment payment = TestFixtures.aPayment(PaymentStatus.COMPLETED);

            given(portOneSignatureVerifier.verify("valid-sig", body)).willReturn(true);
            given(objectMapper.readValue(body, WebhookRequest.class)).willReturn(paidWebhookRequest());
            given(webhookEventCommandService.saveReceivedOrGet(anyString(), anyString(), anyString()))
                    .willReturn(TestFixtures.aWebhookEvent());
            given(paymentRepository.findByPaymentUidWithLock("PAY-001")).willReturn(Optional.of(payment));

            portOneWebhookService.handleWebhook("valid-sig", body);

            verify(paymentCommandService, never()).completePayment(any(), any(), any(), any());
        }

        @Test
        @DisplayName("성공: PAID이지만 금액 불일치 → markFailed 호출 후 리턴 (예외 발생 안함)")
        void success_with_amountMismatch() throws Exception {
            byte[] body = validBody();
            Payment payment = TestFixtures.aPayment(PaymentStatus.PENDING); // amount=10000L

            given(portOneSignatureVerifier.verify("valid-sig", body)).willReturn(true);
            given(objectMapper.readValue(body, WebhookRequest.class)).willReturn(paidWebhookRequest());
            given(webhookEventCommandService.saveReceivedOrGet(anyString(), anyString(), anyString()))
                    .willReturn(TestFixtures.aWebhookEvent());
            given(paymentRepository.findByPaymentUidWithLock("PAY-001")).willReturn(Optional.of(payment));
            given(portOneClientService.getPayment("PAY-001")).willReturn(
                    new PortOnePaymentResponse(PortOneStatus.NETWORK_ERROR, 15000L, "CARD", LocalDateTime.now() , "",null,null,null));
            portOneWebhookService.handleWebhook("valid-sig", body);

            verify(failureService).markFailed(eq(1L), eq(PaymentErrorReason.AMOUNT_MISMATCH));
            verify(setExpireService).cancelExpiry(1L);
            verify(paymentCommandService, never()).completePayment(any(), any(), any(), any());
        }

        @Test
        @DisplayName("성공: PAID 아닌 상태 → markFailed + cancelExpiry")
        void success_otherStatus() throws Exception {
            byte[] body = validBody();
            Payment payment = TestFixtures.aPayment(PaymentStatus.PENDING);

            given(portOneSignatureVerifier.verify("valid-sig", body)).willReturn(true);
            given(objectMapper.readValue(body, WebhookRequest.class)).willReturn(failedWebhookRequest());
            given(webhookEventCommandService.saveReceivedOrGet(anyString(), anyString(), anyString()))
                    .willReturn(TestFixtures.aWebhookEvent());
            given(paymentRepository.findByPaymentUidWithLock("PAY-001")).willReturn(Optional.of(payment));

            portOneWebhookService.handleWebhook("valid-sig", body);

            verify(failureService).markFailed(eq(1L), eq(PaymentErrorReason.WEBHOOK_FAILED));
            verify(setExpireService).cancelExpiry(1L);
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
