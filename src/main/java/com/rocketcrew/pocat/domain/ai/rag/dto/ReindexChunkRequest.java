package com.rocketcrew.pocat.domain.ai.rag.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * 카드 임베딩 청크 재색인 요청.
 *
 * @param cardIds 재색인 대상 카드 ID 목록
 */
public record ReindexChunkRequest(
        @NotEmpty @Size(max = 100) List<Long> cardIds
) {
}
