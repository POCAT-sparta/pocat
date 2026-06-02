package com.rocketcrew.pocat.domain.auction.controller;

import com.rocketcrew.pocat.domain.auction.service.AuctionBuyoutService;
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
@RequestMapping("/internal/auctions")
@RequiredArgsConstructor
public class InternalAuctionController {

    private final AuctionBuyoutService auctionBuyoutService;

    @Value("${pocat.internal.token}")
    private String internalToken;

    /**
     * 결제 대기 중인 즉시 구매 경매를 복구.
     * 배치 시스템에서만 호출되는 내부 API.
     *
     * @param id 경매 ID
     * @param token X-Internal-Token 헤더 검증
     * @return 200 OK (멱등성 보장)
     */
    @PostMapping("/{id}/recover-buyout")
    public ResponseEntity<Void> recoverBuyout(
            @PathVariable Long id,
            @RequestHeader("X-Internal-Token") String token) {

        if (!internalToken.equals(token)) {
            log.warn("내부 API 인증 실패: 잘못된 토큰");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        try {
            boolean recovered = auctionBuyoutService.recoverStalePaymentPendingAuction(id);
            if (recovered) {
                log.info("경매 복구 성공: id={}", id);
            } else {
                log.info("경매 복구 스킵 (이미 처리됨 또는 해당 상태 없음): id={}", id);
            }
        } catch (Exception e) {
            // 멱등성: 오류 발생해도 200 OK 반환 (배치 재시도 처리)
            log.debug("경매 복구 중 예외 발생 (멱등 처리): id={}, error={}", id, e.getMessage());
        }

        return ResponseEntity.ok().build();
    }
}
