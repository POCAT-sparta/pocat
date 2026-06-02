package com.rocketcrew.pocat.domain.auction.controller;

import com.rocketcrew.pocat.domain.auction.service.AuctionBuyoutService;
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
@RequestMapping("/internal/auctions")
@RequiredArgsConstructor
public class InternalAuctionController {

    private final AuctionBuyoutService auctionBuyoutService;

    /**
     * 결제 대기 중인 즉시 구매 경매를 복구.
     * 배치 시스템에서만 호출되는 내부 API.
     *
     * @param id 경매 ID
     * @return 200 OK (멱등성 보장)
     */
    @PostMapping("/{id}/recover-buyout")
    public ResponseEntity<Void> recoverBuyout(@PathVariable @Positive Long id) {
        try {
            auctionBuyoutService.recoverStalePaymentPendingAuction(id);
            log.info("경매 복구 처리: auctionId={}", id);
            return ResponseEntity.ok().build();
        } catch (IllegalStateException e) {
            log.info("경매 복구 스킵 (상태 불일치): auctionId={}, reason={}", id, e.getMessage());
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            log.error("경매 복구 실패: auctionId={}", id, e);
            return ResponseEntity.internalServerError().build();
        }
    }
}
