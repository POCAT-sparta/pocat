package com.rocketcrew.pocat.domain.ai.rag.controller;

import com.rocketcrew.pocat.domain.ai.rag.dto.ReindexChunkRequest;
import com.rocketcrew.pocat.domain.ai.rag.dto.ReindexChunkResponse;
import com.rocketcrew.pocat.domain.ai.rag.service.AiReindexChunkService;
import com.rocketcrew.pocat.global.dto.ApiResponseDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * AI 카드 임베딩 재색인 internal API.
 *
 * <p>pocat-batch에서만 호출되는 내부 API. {@code /internal/**} 경로는
 * {@code InternalTokenAuthFilter}에서 X-Internal-Token 헤더를 검증한다.
 */
@Slf4j
@RestController
@RequestMapping("/internal/ai")
@RequiredArgsConstructor
public class InternalAiController {

    private final AiReindexChunkService aiReindexChunkService;

    /**
     * 카드 임베딩 청크 재색인.
     *
     * <p>요청받은 cardId 목록 중 ES에 기인덱싱되지 않은 카드만 임베딩한다.
     *
     * @param idempotencyKey 멱등성 키 (필수)
     * @param request 재색인 대상 카드 ID 목록
     * @return 처리 통계
     */
    @PostMapping("/reindex-cards")
    public ResponseEntity<ApiResponseDto<ReindexChunkResponse>> reindexCards(
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestBody ReindexChunkRequest request
    ) {
        log.info("[AI_REINDEX_CHUNK] 카드 임베딩 청크 재색인 요청: idempotencyKey={}, cardCount={}",
                idempotencyKey, request.cardIds().size());

        ReindexChunkResponse response = aiReindexChunkService.reindex(request.cardIds());
        return ResponseEntity.ok(ApiResponseDto.success(HttpStatus.OK, response));
    }
}
