package com.rocketcrew.pocat.domain.auction.controller;

import io.swagger.v3.oas.annotations.Hidden;
import com.rocketcrew.pocat.domain.auction.service.AuctionBuyoutService;
import com.rocketcrew.pocat.domain.auction.service.AuctionLifecycleService;
import com.rocketcrew.pocat.global.dto.ApiResponseDto;
import com.rocketcrew.pocat.global.exception.common.ErrorCode;
import com.rocketcrew.pocat.global.exception.domain.AuctionException;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@Validated
@Hidden
@RestController
@RequestMapping("/internal/auctions")
@RequiredArgsConstructor
public class InternalAuctionController {

    private final AuctionBuyoutService auctionBuyoutService;
    private final AuctionLifecycleService auctionLifecycleService;

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

    /**
     * 검수 승인된 경매를 ACTIVE 상태로 전환.
     * 배치 시스템에서만 호출되는 내부 API.
     *
     * @param id 경매 ID
     * @return 200 OK + 활성화 여부 (락 충돌·대상 없음 시 false)
     */
    @PostMapping("/{id}/activate")
    public ResponseEntity<ApiResponseDto<Boolean>> activate(@PathVariable @Positive Long id) {
        try {
            boolean activated = auctionLifecycleService.activateApprovedAuction(id);
            return ResponseEntity.ok(ApiResponseDto.success(HttpStatus.OK, activated));
        } catch (AuctionException e) {
            if (isSkippableAuctionException(e)) {
                log.info("경매 활성화 스킵: auctionId={}, reason={}", id, e.getMessage());
                return ResponseEntity.ok(ApiResponseDto.success(HttpStatus.OK, false));
            }
            throw e;
        }
    }

    /**
     * 종료 시각이 지난 ACTIVE 경매를 마감 처리.
     * 배치 시스템에서만 호출되는 내부 API.
     *
     * @param id 경매 ID
     * @return 200 OK + 마감 여부 (락 충돌·대상 없음 시 false)
     */
    @PostMapping("/{id}/close-expired")
    public ResponseEntity<ApiResponseDto<Boolean>> closeExpired(@PathVariable @Positive Long id) {
        try {
            boolean closed = auctionLifecycleService.closeExpiredAuction(id);
            return ResponseEntity.ok(ApiResponseDto.success(HttpStatus.OK, closed));
        } catch (AuctionException e) {
            if (isSkippableAuctionException(e)) {
                log.info("경매 마감 스킵: auctionId={}, reason={}", id, e.getMessage());
                return ResponseEntity.ok(ApiResponseDto.success(HttpStatus.OK, false));
            }
            throw e;
        }
    }

    private boolean isSkippableAuctionException(AuctionException e) {
        return e.getErrorCode() == ErrorCode.AUCTION_LOCK_FAILED
                || e.getErrorCode() == ErrorCode.AUCTION_NOT_FOUND;
    }
}
