package com.rocketcrew.pocat.domain.refund.controller;

import com.rocketcrew.pocat.domain.refund.service.RefundCommandService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

@Slf4j
@RestController
@RequestMapping("/internal/refunds")
@RequiredArgsConstructor
public class InternalRefundController {

    private final RefundCommandService refundCommandService;

    @Value("${pocat.internal.token}")
    private String internalToken;

    /**
     * 환불 재시도.
     * 배치 시스템에서만 호출되는 내부 API.
     *
     * @param id 환불 ID
     * @param token X-Internal-Token 헤더 검증
     * @return 200 OK (멱등성 보장)
     */
    @PostMapping("/{id}/retry")
    public ResponseEntity<Void> retryRefund(
            @PathVariable Long id,
            @RequestHeader("X-Internal-Token") String token) {

        if (!MessageDigest.isEqual(
                internalToken.getBytes(StandardCharsets.UTF_8),
                token.getBytes(StandardCharsets.UTF_8))) {
            log.warn("내부 API 인증 실패: 잘못된 토큰");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        try {
            refundCommandService.retryRefund(id);
            log.info("환불 재시도 성공: refundId={}", id);
            return ResponseEntity.ok().build();
        } catch (IllegalStateException | IllegalArgumentException e) {
            // 이미 처리됨 또는 상태 불일치 → 멱등 성공으로 처리
            log.info("환불 재시도 스킵 (이미 처리됨): refundId={}, reason={}", id, e.getMessage());
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            // 예기치 않은 오류 → 배치가 재시도할 수 있도록 5xx 반환
            log.error("환불 재시도 실패: refundId={}", id, e);
            return ResponseEntity.internalServerError().build();
        }
    }
}
