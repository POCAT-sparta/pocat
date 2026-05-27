package com.rocketcrew.pocat.domain.ai.assistant.dto;

import java.util.List;

/**
 * AI 어시스턴트 채팅 응답 DTO.
 *
 * @param reply AI 응답 메시지
 * @param sessionId 세션 UUID
 * @param toolsUsed 사용된 Tool 목록
 * @param promptTokens 소비한 프롬프트 토큰 수
 * @param completionTokens 생성된 완료 토큰 수
 */
public record AiChatResponse(
        String reply,
        String sessionId,
        List<String> toolsUsed,
        Integer promptTokens,
        Integer completionTokens
) {
}
