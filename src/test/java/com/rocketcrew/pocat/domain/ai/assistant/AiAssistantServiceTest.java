package com.rocketcrew.pocat.domain.ai.assistant;

import com.rocketcrew.pocat.domain.ai.assistant.dto.AiChatRequest;
import com.rocketcrew.pocat.domain.ai.assistant.dto.AiChatResponse;
import com.rocketcrew.pocat.global.exception.common.ServiceException;
import com.rocketcrew.pocat.domain.ai.assistant.service.AiAssistantService;
import com.rocketcrew.pocat.domain.ai.assistant.service.AiChatSessionService;
import com.rocketcrew.pocat.domain.ai.assistant.tools.AuctionTool;
import com.rocketcrew.pocat.domain.ai.assistant.tools.BidTool;
import com.rocketcrew.pocat.domain.ai.assistant.tools.CardSearchTool;
import com.rocketcrew.pocat.global.metrics.AiUsageMetrics;
import com.rocketcrew.pocat.domain.ai.rag.service.RagService;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatResponse;

import java.lang.reflect.Method;
import java.util.List;
import org.springframework.ai.document.Document;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willDoNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("AiAssistantService")
class AiAssistantServiceTest {

    @InjectMocks
    private AiAssistantService aiAssistantService;

    @Mock
    private ChatClient chatClient;

    @Mock
    private CardSearchTool cardSearchTool;

    @Mock
    private AuctionTool auctionTool;

    @Mock
    private BidTool bidTool;

    @Mock
    private RagService ragService;

    @Mock
    private AiUsageMetrics aiUsageMetrics;

    @Mock
    private AiChatSessionService sessionService;

    @Mock
    private ChatClient.ChatClientRequestSpec requestSpec;

    @Mock
    private ChatClient.CallResponseSpec callResponseSpec;

    private static final Long USER_ID = 1L;
    private static final String AI_REPLY = "피카츄 카드 현재 시세는 5만원입니다.";

    @BeforeEach
    void setUp() {
        // stub full ChatClient chain: prompt() -> system() -> user() -> call() -> content()
        given(chatClient.prompt()).willReturn(requestSpec);
        given(requestSpec.system(anyString())).willReturn(requestSpec);
        given(requestSpec.user(anyString())).willReturn(requestSpec);
        given(requestSpec.tools(any(), any(), any())).willReturn(requestSpec);
        given(requestSpec.call()).willReturn(callResponseSpec);

        // stub chatResponse() chain (code now uses chatResponse() not content())
        ChatResponse defaultChatResponse = mock(ChatResponse.class, org.mockito.Mockito.RETURNS_DEEP_STUBS);
        given(defaultChatResponse.getResult().getOutput().getText()).willReturn(AI_REPLY);
        ChatResponseMetadata defaultMetadata = mock(ChatResponseMetadata.class);
        given(defaultChatResponse.getMetadata()).willReturn(defaultMetadata);
        given(defaultMetadata.getUsage()).willReturn(null);
        given(callResponseSpec.chatResponse()).willReturn(defaultChatResponse);

        // stub RAG
        given(ragService.search(anyString())).willReturn(List.of(new Document("test card context")));
        given(ragService.buildContext(any())).willReturn("관련 문서를 찾을 수 없습니다.");

        // stub sessionService defaults
        given(sessionService.getOrCreateSession(anyLong(), anyString())).willReturn(10L);
        willDoNothing().given(sessionService).validateSessionOwner(anyString(), anyLong());
        given(sessionService.getRecentMessages(anyLong(), anyInt())).willReturn(List.of());
        willDoNothing().given(sessionService).addMessage(anyLong(), anyString(), anyString(), anyInt());

        // stub metrics
        willDoNothing().given(aiUsageMetrics).recordUsage(anyInt(), anyInt(), anyLong(), anyString());
        willDoNothing().given(aiUsageMetrics).recordError(anyString(), anyString());
    }

    // ---------------------------------------------------------------
    // chat
    // ---------------------------------------------------------------
    @Nested
    @DisplayName("chat()")
    class Chat {

        @Test
        @DisplayName("신규 세션: sessionId null 시 UUID 생성 후 응답 반환")
        void chat_new_session() {
            // given
            AiChatRequest request = new AiChatRequest("포켓몬 카드 시세 알려줘", null);

            // when
            AiChatResponse response = aiAssistantService.chat(USER_ID, request);

            // then
            assertThat(response).isNotNull();
            assertThat(response.reply()).isEqualTo(AI_REPLY);
            assertThat(response.sessionId()).isNotNull();
            verify(sessionService).getOrCreateSession(eq(USER_ID), anyString());
        }

        @Test
        @DisplayName("기존 세션: sessionId 제공 시 validateSessionOwner 호출")
        void chat_existing_session() {
            // given
            String existingSessionId = "existing-session-uuid";
            AiChatRequest request = new AiChatRequest("뮤츠 가격 알려줘", existingSessionId);

            // when
            AiChatResponse response = aiAssistantService.chat(USER_ID, request);

            // then
            assertThat(response).isNotNull();
            assertThat(response.sessionId()).isEqualTo(existingSessionId);
            verify(sessionService).validateSessionOwner(existingSessionId, USER_ID);
        }

        @Test
        @DisplayName("chat 실패: chatClient 예외 발생 시 RuntimeException 전파 및 에러 메트릭 기록")
        void chat_throws_on_client_exception() {
            // given
            AiChatRequest request = new AiChatRequest("테스트 메시지", null);
            given(callResponseSpec.chatResponse()).willThrow(new RuntimeException("LLM unavailable"));

            // when / then
            assertThatThrownBy(() -> aiAssistantService.chat(USER_ID, request))
                    .isInstanceOf(ServiceException.class);
            verify(aiUsageMetrics).recordError("CHAT_FAILED", "gemini-2.5-flash");
        }

        @Test
        @DisplayName("메트릭 기록: 성공 후 aiUsageMetrics.recordUsage 호출")
        void chat_records_metrics() {
            // given
            AiChatRequest request = new AiChatRequest("메트릭 테스트 질문", null);

            // when
            aiAssistantService.chat(USER_ID, request);

            // then
            // usage is null in @BeforeEach stub → tokens default to 0
            verify(aiUsageMetrics).recordUsage(eq(0), eq(0), anyLong(), anyString());
        }
    }

    // ---------------------------------------------------------------
    // CircuitBreaker + RateLimiter (infra-fix #116)
    // ---------------------------------------------------------------
    @Nested
    @DisplayName("CircuitBreaker / RateLimiter (#116)")
    class CircuitBreakerAndRateLimiter {

        @Test
        @DisplayName("chat() 메서드에 @CircuitBreaker 어노테이션이 선언되어 있어야 한다")
        void circuitBreaker_chat_triggersCircuitBreakerOnMultipleFailures() throws NoSuchMethodException {
            // The fix must add @CircuitBreaker to the chat() method.
            // This test will FAIL until the annotation is added.
            Method chatMethod = AiAssistantService.class.getMethod("chat", Long.class, AiChatRequest.class);
            CircuitBreaker cb = chatMethod.getAnnotation(CircuitBreaker.class);
            assertThat(cb)
                    .as("chat() must be annotated with @CircuitBreaker")
                    .isNotNull();
            assertThat(cb.name()).isEqualTo("aiService");
        }

        @Test
        @DisplayName("chat() 메서드에 @RateLimiter 어노테이션이 선언되어 있어야 한다")
        void rateLimiter_chat_isAnnotatedWithRateLimiter() throws NoSuchMethodException {
            Method chatMethod = AiAssistantService.class.getMethod("chat", Long.class, AiChatRequest.class);
            io.github.resilience4j.ratelimiter.annotation.RateLimiter rl =
                    chatMethod.getAnnotation(io.github.resilience4j.ratelimiter.annotation.RateLimiter.class);
            assertThat(rl)
                    .as("chat() must be annotated with @RateLimiter")
                    .isNotNull();
            assertThat(rl.name()).isEqualTo("aiEndpoint");
            assertThat(rl.fallbackMethod()).isEqualTo("chatRateLimitFallback");
        }

        @Test
        @DisplayName("chat() 성공 시 recordUsage에 실제 토큰 값(>0)이 전달되어야 한다")
        void chat_extractsTokensFromChatResponse() {
            // Given: ChatResponse with real usage metadata returned from chatResponseSpec
            ChatResponse chatResponse = mock(ChatResponse.class, org.mockito.Mockito.RETURNS_DEEP_STUBS);
            given(chatResponse.getResult().getOutput().getText()).willReturn(AI_REPLY);
            ChatResponseMetadata metadata = mock(ChatResponseMetadata.class);
            Usage usage = mock(Usage.class);
            given(chatResponse.getMetadata()).willReturn(metadata);
            given(metadata.getUsage()).willReturn(usage);
            given(usage.getPromptTokens()).willReturn(120);
            given(usage.getCompletionTokens()).willReturn(80);
            given(callResponseSpec.chatResponse()).willReturn(chatResponse);

            AiChatRequest request = new AiChatRequest("토큰 추출 테스트", null);

            // when
            aiAssistantService.chat(USER_ID, request);

            // then: after fix, recordUsage must NOT be called with all-zero tokens
            // This will FAIL until the implementation uses ChatResponse.getMetadata().getUsage()
            // Verify recordUsage is called with promptTokens=120, completionTokens=80
            verify(aiUsageMetrics).recordUsage(
                    org.mockito.ArgumentMatchers.eq(120),
                    org.mockito.ArgumentMatchers.eq(80),
                    anyLong(),
                    anyString()
            );
        }
    }

    // ---------------------------------------------------------------
    // [RED] #158 환각 방어 Layer2 — RAG 빈 결과 시 LLM 미호출
    // ---------------------------------------------------------------
    @Nested
    @DisplayName("[RED #158] 환각 방어 Layer2 — RAG 빈 결과")
    class RagEmptyResultDefense {

        @Test
        @DisplayName("[RED] RAG 빈 결과 시 ChatClient.prompt() 미호출")
        void chat_ragEmptyResult_doesNotCallLlm() {
            // given: RAG 검색 결과 없음
            given(ragService.search(anyString())).willReturn(List.of());
            AiChatRequest request = new AiChatRequest("관련 카드 없는 질문", null);

            // when
            // RED: 현재 구현은 RAG 빈 결과에도 LLM 호출함
            // GREEN 조건: ragResults.isEmpty() 시 LLM 미호출 + 안내 메시지 반환
            AiChatResponse response = aiAssistantService.chat(USER_ID, request);

            // then: LLM이 호출되지 않아야 함
            verify(chatClient, org.mockito.Mockito.never()).prompt();
            assertThat(response).isNotNull();
        }

        @Test
        @DisplayName("[RED] RAG 빈 결과 시 응답 메시지에 '관련 카드 정보를 찾을 수 없습니다' 포함")
        void chat_ragEmptyResult_returnsGuideMessage() {
            // given: RAG 검색 결과 없음
            given(ragService.search(anyString())).willReturn(List.of());
            AiChatRequest request = new AiChatRequest("RAG 빈 결과 테스트", null);

            // when
            AiChatResponse response = aiAssistantService.chat(USER_ID, request);

            // then
            // RED: 현재 구현은 LLM 응답("피카츄 카드 현재 시세는 5만원입니다.")을 반환
            // GREEN 조건: reply에 "관련 카드 정보를 찾을 수 없습니다" 포함
            assertThat(response.reply())
                    .as("RAG 빈 결과 시 안내 메시지 반환해야 함 (현재 LLM 응답 반환 → RED)")
                    .contains("관련 카드 정보를 찾을 수 없습니다");
        }
    }

    // ---------------------------------------------------------------
    // AI 장애 시나리오
    // ---------------------------------------------------------------
    @Nested
    @DisplayName("AI 장애 시나리오")
    class AiFaultScenarios {

        @Test
        @DisplayName("@CircuitBreaker 어노테이션 선언 확인")
        void circuitBreakerAnnotationDeclared() throws Exception {
            Method chatMethod = AiAssistantService.class.getMethod("chat", Long.class, AiChatRequest.class);
            io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker cb =
                    chatMethod.getAnnotation(io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker.class);
            assertThat(cb)
                    .as("chat() must be annotated with @CircuitBreaker(name=\"aiService\")")
                    .isNotNull();
            assertThat(cb.name()).isEqualTo("aiService");
            assertThat(cb.fallbackMethod()).isEqualTo("chatFallback");
        }

        @Test
        @DisplayName("@RateLimiter 어노테이션 선언 확인")
        void rateLimiterAnnotationDeclared() throws Exception {
            Method chatMethod = AiAssistantService.class.getMethod("chat", Long.class, AiChatRequest.class);
            io.github.resilience4j.ratelimiter.annotation.RateLimiter rl =
                    chatMethod.getAnnotation(io.github.resilience4j.ratelimiter.annotation.RateLimiter.class);
            assertThat(rl)
                    .as("chat() must be annotated with @RateLimiter(name=\"aiEndpoint\", fallbackMethod=\"chatRateLimitFallback\")")
                    .isNotNull();
            assertThat(rl.name()).isEqualTo("aiEndpoint");
            assertThat(rl.fallbackMethod()).isEqualTo("chatRateLimitFallback");
        }

        @Test
        @DisplayName("ChatClient 예외 발생 시 ServiceException(AI_SERVICE_UNAVAILABLE) 반환")
        void chatClientException_throwsServiceException() {
            // given
            AiChatRequest request = new AiChatRequest("테스트 메시지", null);
            given(callResponseSpec.chatResponse()).willThrow(new RuntimeException("LLM timeout"));

            // when / then
            assertThatThrownBy(() -> aiAssistantService.chat(USER_ID, request))
                    .isInstanceOf(ServiceException.class);
            verify(aiUsageMetrics).recordError("CHAT_FAILED", "gemini-2.5-flash");
        }

        @Test
        @DisplayName("fallback 메서드 chatFallback 존재 확인")
        void chatFallbackMethodExists() throws Exception {
            Method fallback = AiAssistantService.class.getDeclaredMethod(
                    "chatFallback", Long.class, AiChatRequest.class, Throwable.class);
            assertThat(fallback)
                    .as("chatFallback(Long, AiChatRequest, Throwable) must exist")
                    .isNotNull();
        }

        @Test
        @DisplayName("fallback 메서드 chatRateLimitFallback 존재 확인")
        void chatRateLimitFallbackMethodExists() throws Exception {
            Method fallback = AiAssistantService.class.getDeclaredMethod(
                    "chatRateLimitFallback", Long.class, AiChatRequest.class,
                    io.github.resilience4j.ratelimiter.RequestNotPermitted.class);
            assertThat(fallback)
                    .as("chatRateLimitFallback(Long, AiChatRequest, RequestNotPermitted) must exist")
                    .isNotNull();
        }
    }

    // helper to avoid static import ambiguity with Mockito.eq
    private static <T> T eq(T value) {
        return org.mockito.ArgumentMatchers.eq(value);
    }
}
