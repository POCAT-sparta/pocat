package com.rocketcrew.pocat.domain.payment.controller;

import com.rocketcrew.pocat.domain.payment.dto.request.CreatePaymentRequest;
import com.rocketcrew.pocat.domain.payment.dto.response.PaymentResponse;
import com.rocketcrew.pocat.domain.payment.service.PaymentApplicationService;
import com.rocketcrew.pocat.domain.payment.service.PaymentQueryService;
import com.rocketcrew.pocat.domain.payment.service.PaymentWebhookService;
import com.rocketcrew.pocat.global.dto.ApiResponseDto;
import com.rocketcrew.pocat.global.exception.common.ErrorCode;
import com.rocketcrew.pocat.global.exception.domain.PaymentException;
import com.rocketcrew.pocat.global.security.CustomUserDetails;
import com.rocketcrew.pocat.global.util.HttpRequestUtils;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.util.Arrays;
import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api")
public class PaymentController {

    private final PaymentQueryService paymentQueryService;
    private final PaymentWebhookService paymentWebhookService;
    private final PaymentApplicationService paymentApplicationService;

    @Value("${portone.webhook-allowed-ips:}")
    private String allowedIpsConfig;

    /** 6.1 결제 요청 — PG 직접결제 레코드 생성 */
    @PostMapping("/v1/payments")
    public ResponseEntity<ApiResponseDto<PaymentResponse>> createPayment(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @Valid @RequestBody CreatePaymentRequest request) {
        PaymentResponse response = paymentApplicationService.generatePayment(userDetails.getUserId(), request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponseDto.success(HttpStatus.CREATED, response));
    }

    /** 6.2 결제 확정 요청 — PortOne SDK 결제 완료 후 Client Confirm */
    @PatchMapping("/v1/payments/{paymentUid}")
    public ResponseEntity<ApiResponseDto<PaymentResponse>> confirmPayment(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable String paymentUid) {
        PaymentResponse response = paymentApplicationService.confirmPayment(userDetails.getUserId(), paymentUid);
        return ResponseEntity.ok(ApiResponseDto.success(HttpStatus.OK, response));
    }

    /** 6.3 결제 상세 조회 */
    @GetMapping("/v1/payments/{paymentUid}")
    public ResponseEntity<ApiResponseDto<PaymentResponse>> getPayment(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable String paymentUid) {
        PaymentResponse response = paymentQueryService.getPayment(userDetails.getUserId(), paymentUid);
        return ResponseEntity.ok(ApiResponseDto.success(HttpStatus.OK, response));
    }

    /**
     * 6.4 PortOne Webhook 수신 (PUBLIC — PortOne 서버 → 우리 서버)
     * rawBody를 byte[]로 수신하여 HMAC 서명 검증에 원본 바이트를 그대로 사용한다.
     * 역직렬화 후 재직렬화 시 발생하는 바이트 불일치로 서명 검증이 실패하는 문제를 방지.
     * PortOne은 200을 받지 못하면 재전송하므로 서명 검증 통과 후 항상 200 반환.
     */
    @PostMapping("/v1/payments/webhook")
    public ResponseEntity<ApiResponseDto<Void>> handleWebhook(
            @RequestHeader(value = "X-PortOne-Signature", required = false) String signature,
            @RequestBody byte[] rawBody,
            HttpServletRequest request) {
        if (StringUtils.hasText(allowedIpsConfig)) {
            String clientIp = HttpRequestUtils.resolveClientIp(request);
            List<String> allowed = Arrays.asList(allowedIpsConfig.split(","));
            if (allowed.stream().noneMatch(ip -> ip.trim().equals(clientIp))) {
                throw new PaymentException(ErrorCode.WEBHOOK_IP_FORBIDDEN);
            }
        }
        paymentWebhookService.handleWebhook(signature, rawBody);
        return ResponseEntity.ok(ApiResponseDto.success(HttpStatus.OK, null));
    }
}
