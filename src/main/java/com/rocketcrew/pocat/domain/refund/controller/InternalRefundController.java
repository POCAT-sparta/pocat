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

        if (!internalToken.equals(token)) {
            log.warn("내부 API 인증 실패: 잘못된 토큰");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        try {
            refundCommandService.retryRefund(id);
            log.info("환불 재시도 성공: id={}", id);
        } catch (Exception e) {
            // 멱등성: 오류 발생해도 200 OK 반환 (배치 재시도 처리)
            log.debug("환불 재시도 중 예외 발생 (멱등 처리): id={}, error={}", id, e.getMessage());
        }

        return ResponseEntity.ok().build();
    }
}
