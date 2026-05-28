package com.rocketcrew.pocat.domain.payment.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rocketcrew.pocat.domain.payment.dto.request.CreatePaymentRequest;
import com.rocketcrew.pocat.domain.payment.dto.response.PaymentResponse;
import com.rocketcrew.pocat.domain.payment.entity.PaymentStatus;
import com.rocketcrew.pocat.domain.payment.entity.PaymentType;
import com.rocketcrew.pocat.domain.payment.service.PaymentApplicationService;
import com.rocketcrew.pocat.domain.payment.service.PaymentQueryService;
import com.rocketcrew.pocat.domain.payment.service.PaymentWebhookService;
import com.rocketcrew.pocat.global.exception.common.ErrorCode;
import com.rocketcrew.pocat.global.exception.common.GlobalExceptionHandler;
import com.rocketcrew.pocat.global.exception.domain.PaymentException;
import com.rocketcrew.pocat.global.security.CustomUserDetails;
import com.rocketcrew.pocat.support.TestCustomUserDetails;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.MethodParameter;
import org.springframework.data.web.PageableHandlerMethodArgumentResolver;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

import java.time.LocalDateTime;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("PaymentController")
class PaymentControllerTest {

    private MockMvc mockMvc;

    @InjectMocks
    private PaymentController paymentController;

    @Mock
    private PaymentApplicationService paymentApplicationService;

    @Mock
    private PaymentQueryService paymentQueryService;

    @Mock
    private PaymentWebhookService paymentWebhookService;

    private CustomUserDetails userDetails;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        userDetails = new TestCustomUserDetails(1L, "USER");
        mockMvc = MockMvcBuilders.standaloneSetup(paymentController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .setCustomArgumentResolvers(
                        new PageableHandlerMethodArgumentResolver(),
                        new HandlerMethodArgumentResolver() {
                            @Override
                            public boolean supportsParameter(MethodParameter parameter) {
                                return parameter.hasParameterAnnotation(AuthenticationPrincipal.class);
                            }

                            @Override
                            public Object resolveArgument(MethodParameter parameter,
                                                          ModelAndViewContainer mavContainer,
                                                          NativeWebRequest webRequest,
                                                          WebDataBinderFactory binderFactory) {
                                return userDetails;
                            }
                        })
                .build();
    }

    private PaymentResponse samplePaymentResponse() {
        return new PaymentResponse(
                "PAY-001",
                1L,
                10000L,
                PaymentType.PG_DIRECT,
                null,
                PaymentStatus.PENDING,
                null,
                LocalDateTime.now()
        );
    }

    // ── POST /api/v1/payments ──────────────────────────────────────────

    @Nested
    @DisplayName("POST /api/v1/payments")
    class CreatePayment {

        @Test
        @DisplayName("성공: 201 CREATED 와 함께 결제 레코드를 반환한다")
        void success_201() throws Exception {
            CreatePaymentRequest request = new CreatePaymentRequest(1L);
            given(paymentApplicationService.generatePayment(eq(1L), any(CreatePaymentRequest.class)))
                    .willReturn(samplePaymentResponse());

            mockMvc.perform(post("/api/v1/payments")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.status").value(201))
                    .andExpect(jsonPath("$.data.paymentUid").value("PAY-001"));
        }

        @Test
        @DisplayName("실패: 409 — 주문 상태가 PAYMENT_FAILED 아님")
        void fail_409_orderNotFailed() throws Exception {
            CreatePaymentRequest request = new CreatePaymentRequest(1L);
            given(paymentApplicationService.generatePayment(eq(1L), any(CreatePaymentRequest.class)))
                    .willThrow(new PaymentException(ErrorCode.PAYMENT_ORDER_NOT_FAILED));

            mockMvc.perform(post("/api/v1/payments")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("PAYMENT_ORDER_NOT_FAILED"));
        }
    }

    // ── PATCH /api/v1/payments/{paymentUid} ───────────────────────────

    @Nested
    @DisplayName("PATCH /api/v1/payments/{paymentUid}")
    class ConfirmPayment {

        @Test
        @DisplayName("성공: 200 OK 와 함께 결제 완료 정보를 반환한다")
        void success_200() throws Exception {
            PaymentResponse completed = new PaymentResponse(
                    "PAY-001", 1L, 10000L, PaymentType.PG_DIRECT,
                    "CARD", PaymentStatus.COMPLETED, LocalDateTime.now(), LocalDateTime.now());
            given(paymentApplicationService.confirmPayment(1L, "PAY-001")).willReturn(completed);

            mockMvc.perform(patch("/api/v1/payments/PAY-001"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.status").value("COMPLETED"));
        }

        @Test
        @DisplayName("실패: 403 — 구매자 불일치")
        void fail_403_buyerMismatch() throws Exception {
            given(paymentApplicationService.confirmPayment(1L, "PAY-001"))
                    .willThrow(new PaymentException(ErrorCode.PAYMENT_BUYER_MISMATCH));

            mockMvc.perform(patch("/api/v1/payments/PAY-001"))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("PAYMENT_BUYER_MISMATCH"));
        }
    }

    // ── GET /api/v1/payments/{paymentUid} ─────────────────────────────

    @Nested
    @DisplayName("GET /api/v1/payments/{paymentUid}")
    class GetPayment {

        @Test
        @DisplayName("성공: 200 OK 와 함께 결제 상세를 반환한다")
        void success_200() throws Exception {
            given(paymentQueryService.getPayment(1L, "PAY-001")).willReturn(samplePaymentResponse());

            mockMvc.perform(get("/api/v1/payments/PAY-001"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.paymentUid").value("PAY-001"));
        }

        @Test
        @DisplayName("실패: 404 — 결제 없음")
        void fail_404_notFound() throws Exception {
            given(paymentQueryService.getPayment(1L, "PAY-999"))
                    .willThrow(new PaymentException(ErrorCode.PAYMENT_NOT_FOUND));

            mockMvc.perform(get("/api/v1/payments/PAY-999"))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("PAYMENT_NOT_FOUND"));
        }
    }

    // ── POST /api/v1/payments/webhook ─────────────────────────────────

    @Nested
    @DisplayName("POST /api/v1/payments/webhook")
    class HandleWebhook {

        @Test
        @DisplayName("현재 미구현: PortOne 미연동 시 503 반환 (PORTONE_NOT_INTEGRATED)")
        void success_200() throws Exception {
            willThrow(new PaymentException(ErrorCode.PORTONE_NOT_INTEGRATED))
                    .given(paymentWebhookService).handleWebhook(anyString(), any(byte[].class));

            // PORTONE_NOT_INTEGRATED = 503
            mockMvc.perform(post("/api/v1/payments/webhook")
                            .header("X-PortOne-Signature", "test-sig")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}".getBytes()))
                    .andExpect(status().isServiceUnavailable())
                    .andExpect(jsonPath("$.code").value("PORTONE_NOT_INTEGRATED"));
        }

        @Test
        @DisplayName("실패: 400 — 빈 body (WEBHOOK_EMPTY_BODY)")
        void fail_400_emptyBody() throws Exception {
            willThrow(new PaymentException(ErrorCode.WEBHOOK_EMPTY_BODY))
                    .given(paymentWebhookService).handleWebhook(anyString(), any(byte[].class));

            mockMvc.perform(post("/api/v1/payments/webhook")
                            .header("X-PortOne-Signature", "test-sig")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}".getBytes()))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("WEBHOOK_EMPTY_BODY"));
        }

        @Test
        @DisplayName("실패: X-PortOne-Signature 헤더 누락 → 400")
        void fail_missingSignatureHeader() throws Exception {
            // @RequestHeader("X-PortOne-Signature") 는 required=true (기본값)이므로
            // Spring MVC 가 서비스 호출 전에 400 Bad Request 를 자동 반환한다.
            mockMvc.perform(post("/api/v1/payments/webhook")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}".getBytes()))
                    .andExpect(status().isBadRequest());

            verifyNoInteractions(paymentApplicationService);
        }
    }
}
