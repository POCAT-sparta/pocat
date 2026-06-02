package com.rocketcrew.pocat.domain.refund.controller;

import com.rocketcrew.pocat.domain.refund.service.RefundCommandService;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@Validated
@RestController
@RequestMapping("/internal/refunds")
@RequiredArgsConstructor
public class InternalRefundController {

    private final RefundCommandService refundCommandService;

    /**
     * 환불 재시도.
     * 배치 시스템에서만 호출되는 내부 API.
     *
     * @param id 환불 ID
     * @return 200 OK (멱등성 보장)
     */
    @PostMapping("/{id}/retry")
    public ResponseEntity<Void> retryRefund(@PathVariable @Positive Long id) {
        try {
            refundCommandService.retryRefund(id);
            log.info("환불 재시도 처리: refundId={}", id);
            return ResponseEntity.ok().build();
        } catch (IllegalStateException e) {
            log.info("환불 재시도 스킵 (상태 불일치): refundId={}, reason={}", id, e.getMessage());
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            log.error("환불 재시도 실패: refundId={}", id, e);
            return ResponseEntity.internalServerError().build();
        }
    }
}
