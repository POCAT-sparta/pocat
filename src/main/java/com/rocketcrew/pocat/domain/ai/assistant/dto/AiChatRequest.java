package com.rocketcrew.pocat.domain.ai.assistant.dto;

/**
 * AI 어시스턴트 채팅 요청 DTO.
 *
 * @param message 사용자 메시지
 * @param sessionId 선택사항: 세션 UUID (없으면 새로 생성)
 */
public record AiChatRequest(
        String message,
        String sessionId
) {
}
