package com.rocketcrew.pocat.domain.ai.assistant.controller;

import com.rocketcrew.pocat.global.security.CustomUserDetails;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * AI 어시스턴트 SSE 스트리밍 컨트롤러.
 * ChatModel.stream() 기반의 실시간 응답 전송.
 */
@Slf4j
@RestController
@RequestMapping("/api/ai/assistant")
@RequiredArgsConstructor
@PreAuthorize("isAuthenticated()")
public class AiStreamController {

    private final ChatClient chatClient;

    /**
     * SSE를 통한 실시간 스트리밍 응답.
     *
     * @param message 사용자 메시지
     * @param sessionId 세션 ID (선택사항)
     * @param userDetails 인증된 사용자
     * @return ServerSentEvent 스트림
     */
    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<String>> stream(
            @RequestParam String message,
            @RequestParam(required = false) String sessionId,
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        log.info("Starting SSE stream for userId: {}, msgLen={}", userDetails.getUserId(), message != null ? message.length() : 0);

        return Flux.create(sink -> {
            try {
                AtomicInteger eventId = new AtomicInteger(0);

                // ChatClient의 stream() 메서드 활용
                // 실제 구현은 Spring AI 3.0 이상에서 stream() 지원 확인
                Disposable disposable = chatClient.prompt()
                        .user(message)
                        .stream()
                        .content()
                        .doOnNext(chunk -> {
                            // 각 청크를 ServerSentEvent로 변환
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
                            // 완료 이벤트 전송
                            ServerSentEvent<String> doneEvent = ServerSentEvent.<String>builder()
                                    .id(String.valueOf(eventId.incrementAndGet()))
                                    .event("done")
                                    .data("응답이 완료되었습니다")
                                    .build();
                            sink.next(doneEvent);
                            sink.complete();
                            log.info("Stream completed for userId: {}", userDetails.getUserId());
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
