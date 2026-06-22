package com.rocketcrew.pocat.domain.card.controller;

import io.swagger.v3.oas.annotations.Hidden;
import com.rocketcrew.pocat.domain.card.service.CardSyncService;
import com.rocketcrew.pocat.global.dto.ApiResponseDto;
import com.rocketcrew.pocat.global.exception.common.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 카드 동기화 internal API.
 *
 * <p>pocat-batch에서만 호출되는 내부 API. {@code /internal/**} 경로는
 * {@code InternalTokenAuthFilter}에서 X-Internal-Token 헤더를 검증한다.
 */
@Slf4j
@Hidden
@RestController
@RequestMapping("/internal/cards")
@RequiredArgsConstructor
public class InternalCardSyncController {

    private final CardSyncService cardSyncService;

    /**
     * 카드 세트 전체 동기화를 비동기로 시작한다.
     *
     * <p>{@code cardSyncService.syncAll()}은 {@code @Async("syncExecutor")}로 실행되며,
     * 호출 즉시(fire-and-forget) 202 Accepted를 반환한다.
     *
     * @return 202 Accepted, 이미 동기화가 진행 중이면 409 CARD_SYNC_IN_PROGRESS
     */
    @PostMapping("/sync")
    public ResponseEntity<ApiResponseDto<Void>> sync() {
        try {
            cardSyncService.syncAll();
            log.info("[CARD_SYNC] 카드 동기화 요청 접수");
            return ResponseEntity.status(HttpStatus.ACCEPTED).body(ApiResponseDto.success(HttpStatus.ACCEPTED, null));
        } catch (TaskRejectedException e) {
            log.info("[CARD_SYNC] 동기화 요청 거절: queue 포화");
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(ApiResponseDto.error(ErrorCode.CARD_SYNC_IN_PROGRESS.name(), ErrorCode.CARD_SYNC_IN_PROGRESS.getMessage()));
        }
    }
}
