package com.rocketcrew.pocat.domain.payment.controller;

import com.rocketcrew.pocat.domain.payment.dto.request.CreatePaymentRequest;
import com.rocketcrew.pocat.domain.payment.dto.response.PaymentResponse;
import com.rocketcrew.pocat.domain.payment.service.PaymentCommandService;
import com.rocketcrew.pocat.domain.payment.service.PaymentQueryService;
import com.rocketcrew.pocat.global.dto.ApiResponseDto;
import com.rocketcrew.pocat.global.security.CustomUserDetails;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/payments")
public class PaymentController {

    private final PaymentCommandService paymentCommandService;
    private final PaymentQueryService paymentQueryService;

    /** 6.1 결제 요청 — PG 직접결제 레코드 생성 */
    @PostMapping
    public ResponseEntity<ApiResponseDto<PaymentResponse>> createPayment(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @Valid @RequestBody CreatePaymentRequest request) {
        PaymentResponse response = paymentCommandService.createPayment(userDetails.getUserId(), request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponseDto.success(HttpStatus.CREATED, response));
    }

    /** 6.2 결제 확정 요청 — PortOne SDK 결제 완료 후 Client Confirm */
    @PatchMapping("/{paymentUid}")
    public ResponseEntity<ApiResponseDto<PaymentResponse>> confirmPayment(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable String paymentUid) {
        PaymentResponse response = paymentCommandService.confirmPayment(userDetails.getUserId(), paymentUid);
        return ResponseEntity.ok(ApiResponseDto.success(HttpStatus.OK, response));
    }

    /** 6.3 결제 상세 조회 */
    @GetMapping("/{paymentUid}")
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
    @PostMapping("/webhook")
    public ResponseEntity<ApiResponseDto<Void>> handleWebhook(
            @RequestHeader("X-PortOne-Signature") String signature,
            @RequestBody byte[] rawBody) {
        paymentCommandService.handleWebhook(signature, rawBody);
        return ResponseEntity.ok(ApiResponseDto.success(HttpStatus.OK, null));
    }
}
