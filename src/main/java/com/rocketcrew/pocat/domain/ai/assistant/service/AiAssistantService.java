package com.rocketcrew.pocat.domain.ai.assistant.service;

import com.rocketcrew.pocat.domain.ai.assistant.dto.AiChatRequest;
import com.rocketcrew.pocat.domain.ai.assistant.dto.AiChatResponse;
import com.rocketcrew.pocat.domain.ai.assistant.tools.AuctionTool;
import com.rocketcrew.pocat.domain.ai.assistant.tools.BidTool;
import com.rocketcrew.pocat.domain.ai.assistant.tools.CardSearchTool;
import com.rocketcrew.pocat.domain.ai.monitoring.AiUsageMetrics;
import com.rocketcrew.pocat.domain.ai.rag.service.RagService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * AI 어시스턴트 서비스 (Tool Calling + RAG 지원).
 * 멀티턴 대화, 세션 관리, RAG 컨텍스트 주입 포함.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AiAssistantService {

    private final ChatClient chatClient;
    private final CardSearchTool cardSearchTool;
    private final AuctionTool auctionTool;
    private final BidTool bidTool;
    private final RagService ragService;
    private final AiUsageMetrics aiUsageMetrics;
    private final AiChatSessionService sessionService;

    private static final int MAX_HISTORY_TURNS = 10;
    private static final String MODEL_NAME = "gemini-1.5-flash";

    /**
     * 사용자와의 채팅 상호작용.
     * Tool Calling + RAG 컨텍스트 활용.
     *
     * @param userId 사용자 ID
     * @param request 채팅 요청
     * @return 채팅 응답
     */
    @Transactional
    public AiChatResponse chat(Long userId, AiChatRequest request) {
        log.info("Processing chat for userId: {}, message: {}", userId, request.message());

        try {
            // 1. 세션 조회 또는 생성
            String sessionId = request.sessionId() != null ? request.sessionId() : UUID.randomUUID().toString();
            Long chatSessionId = sessionService.getOrCreateSession(userId, sessionId);

            // 2. 세션 소유자 검증
            sessionService.validateSessionOwner(sessionId, userId);

            // 3. 최근 10턴 메시지 로드
            List<String> recentHistory = sessionService.getRecentMessages(chatSessionId, MAX_HISTORY_TURNS);

            // 4. RAG 검색으로 컨텍스트 구성
            List<Document> ragResults = ragService.search(request.message());
            String ragContext = ragService.buildContext(ragResults);

            // 5. ChatClient 호출 (Tool Calling + RAG)
            String response = chatClient.prompt()
                    .system("당신은 POCAT 카드 거래 플랫폼 어시스턴트입니다. 사용자가 카드, 경매, 입찰에 관한 질문을 할 때 정확하고 도움이 되는 정보를 제공하세요.\n"
                            + "다음의 RAG 컨텍스트를 활용하여 답변하세요:\n" + ragContext)
                    .user(request.message())
                    .tools(cardSearchTool, auctionTool, bidTool)
                    .call()
                    .content();

            // 6. 메시지 저장
            sessionService.addMessage(chatSessionId, "user", request.message(), 0);
            sessionService.addMessage(chatSessionId, "assistant", response, 0);

            // 7. 메트릭 기록
            aiUsageMetrics.recordUsage(0, 0, 0L, MODEL_NAME);

            // 8. 응답 반환
            return new AiChatResponse(
                    response,
                    sessionId,
                    List.of("CardSearchTool", "AuctionTool"),
                    0,
                    0
            );
        } catch (Exception e) {
            log.error("Chat processing failed for userId: {}", userId, e);
            aiUsageMetrics.recordError("CHAT_FAILED", MODEL_NAME);
            throw new RuntimeException("채팅 처리 중 오류 발생: " + e.getMessage(), e);
        }
    }
}
