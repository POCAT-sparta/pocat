package com.rocketcrew.pocat.domain.ai.rag.dto;

/**
 * 카드 임베딩 청크 재색인 결과.
 *
 * @param processedCount 처리 시도한 카드 수
 * @param skippedCount   기인덱싱되어 스킵한 카드 수
 * @param indexedCount   새로 임베딩에 성공한 카드 수
 * @param failedCount    임베딩 실패한 카드 수
 * @param rateLimited    rate limit 도달로 잔여 처리를 중단했는지 여부
 */
public record ReindexChunkResponse(
        int processedCount,
        int skippedCount,
        int indexedCount,
        int failedCount,
        boolean rateLimited
) {
}
