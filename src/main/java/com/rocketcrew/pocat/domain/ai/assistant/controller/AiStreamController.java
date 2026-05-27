package com.rocketcrew.pocat.domain.ai.assistant.controller;

import com.rocketcrew.pocat.domain.ai.assistant.service.AiChatSessionService;
import com.rocketcrew.pocat.domain.ai.rag.service.RagService;
import com.rocketcrew.pocat.global.security.CustomUserDetails;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * AI 어시스턴트 SSE 스트리밍 컨트롤러.
 * ChatModel.stream() 기반의 실시간 응답 전송.
 * 세션 히스토리 및 RAG 컨텍스트 활용.
 */
@Slf4j
@Validated
@RestController
@RequestMapping("/api/ai/assistant")
@RequiredArgsConstructor
@PreAuthorize("isAuthenticated()")
public class AiStreamController {

    private final ChatClient chatClient;
    private final AiChatSessionService sessionService;
    private final RagService ragService;

    private static final int MAX_HISTORY_TURNS = 10;

    /**
     * SSE를 통한 실시간 스트리밍 응답.
     * 세션 히스토리와 RAG 컨텍스트를 활용한 멀티턴 스트리밍.
     *
     * @param message 사용자 메시지
     * @param sessionId 세션 ID (없으면 새 세션 생성)
     * @param userDetails 인증된 사용자
     * @return ServerSentEvent 스트림
     */
    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<String>> stream(
            @RequestParam @NotBlank @Size(max = 2000) String message,
            @RequestParam(required = false) String sessionId,
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        Long userId = userDetails.getUserId();
        String resolvedSessionId = (sessionId != null && !sessionId.isBlank()) ? sessionId : UUID.randomUUID().toString();

        log.info("Starting SSE stream for userId: {}, msgLen={}", userId, message != null ? message.length() : 0);

        // Session setup (blocking, done in calling thread before reactive pipeline)
        Long chatSessionId = sessionService.getOrCreateSession(userId, resolvedSessionId);
        sessionService.validateSessionOwner(resolvedSessionId, userId);
        List<String> recentHistory = sessionService.getRecentMessages(chatSessionId, MAX_HISTORY_TURNS);

        // RAG context
        List<Document> ragResults = ragService.search(message);
        String ragContext = ragService.buildContext(ragResults);

        String historyContext = recentHistory.isEmpty() ? "" :
                "\n\n대화 이력:\n" + String.join("\n", recentHistory);
        String systemPrompt = "당신은 POCAT 카드 거래 플랫폼 어시스턴트입니다. 사용자가 카드, 경매, 입찰에 관한 질문을 할 때 정확하고 도움이 되는 정보를 제공하세요.\n"
                + "다음의 RAG 컨텍스트를 활용하여 답변하세요:\n" + ragContext + historyContext;

        final Long finalChatSessionId = chatSessionId;

        return Flux.create(sink -> {
            try {
                AtomicInteger eventId = new AtomicInteger(0);
                StringBuilder responseBuilder = new StringBuilder();

                Disposable disposable = chatClient.prompt()
                        .system(systemPrompt)
                        .user(message)
                        .stream()
                        .content()
                        .doOnNext(chunk -> {
                            responseBuilder.append(chunk);
                            ServerSentEvent<String> event = ServerSentEvent.<String>builder()
                                    .id(String.valueOf(eventId.incrementAndGet()))
                                    .event("message")
                                    .data(chunk)
                                    .retry(Duration.ofSeconds(3))
                                    .build();
                            sink.next(event);
                        })
                        .doOnError(error -> {
                            log.error("Stream error: {}", error.getMessage(), error);
                            ServerSentEvent<String> errorEvent = ServerSentEvent.<String>builder()
                                    .id(String.valueOf(eventId.incrementAndGet()))
                                    .event("error")
                                    .data("스트리밍 처리 중 오류가 발생했습니다.")
                                    .build();
                            sink.next(errorEvent);
                            sink.complete();
                        })
                        .doOnComplete(() -> {
                            Mono.fromRunnable(() -> {
                                sessionService.addMessage(finalChatSessionId, "user", message, 0);
                                sessionService.addMessage(finalChatSessionId, "assistant", responseBuilder.toString(), 0);
                            }).subscribeOn(Schedulers.boundedElastic())
                              .subscribe(null, err -> log.error("Failed to persist session messages for userId={}: {}", userId, err.getMessage()));

                            ServerSentEvent<String> doneEvent = ServerSentEvent.<String>builder()
                                    .id(String.valueOf(eventId.incrementAndGet()))
                                    .event("done")
                                    .data("응답이 완료되었습니다")
                                    .build();
                            sink.next(doneEvent);
                            sink.complete();
                            log.info("Stream completed for userId: {}", userId);
                        })
                        .subscribe();

                sink.onCancel(disposable::dispose);

            } catch (Exception e) {
                log.error("Error in stream processing: {}", e.getMessage(), e);
                sink.error(e);
            }
        });
    }
}
