package com.rocketcrew.pocat.domain.ai.monitoring;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * AI 서비스 메트릭 기록 (Prometheus/Micrometer).
 * 토큰 소비, 응답 시간, 에러율 추적.
 */
@Slf4j
@Component
public class AiUsageMetrics {

    private final MeterRegistry meterRegistry;

    private final Counter promptTokenCounter;
    private final Counter completionTokenCounter;
    private final Timer responseTimer;
    private final Counter errorCounter;

    public AiUsageMetrics(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;

        // 프롬프트 토큰 카운터
        this.promptTokenCounter = Counter.builder("ai.tokens.prompt")
                .description("AI 프롬프트 토큰 누적")
                .register(meterRegistry);

        // 완료 토큰 카운터
        this.completionTokenCounter = Counter.builder("ai.tokens.completion")
                .description("AI 완료 토큰 누적")
                .register(meterRegistry);

        // 응답 시간 타이머
        this.responseTimer = Timer.builder("ai.response.time")
                .description("AI 응답 처리 시간")
                .publishPercentiles(0.5, 0.95, 0.99)
                .register(meterRegistry);

        // 에러 카운터
        this.errorCounter = Counter.builder("ai.errors.total")
                .description("AI 서비스 에러 누적")
                .register(meterRegistry);
    }

    /**
     * AI 사용 현황 기록.
     *
     * @param promptTokens 프롬프트 토큰 수
     * @param completionTokens 완료 토큰 수
     * @param latencyMs 응답 지연 시간 (ms)
     * @param model 사용된 모델명
     */
    public void recordUsage(int promptTokens, int completionTokens, long latencyMs, String model) {
        try {
            promptTokenCounter.increment(promptTokens);
            completionTokenCounter.increment(completionTokens);

            if (latencyMs > 0) {
                responseTimer.record(java.time.Duration.ofMillis(latencyMs));
            }

            log.debug("AI usage recorded: promptTokens={}, completionTokens={}, latency={}ms, model={}",
                    promptTokens, completionTokens, latencyMs, model);
        } catch (Exception e) {
            log.error("Failed to record AI usage metrics: {}", e.getMessage(), e);
        }
    }

    /**
     * 에러 발생 기록.
     *
     * @param errorType 에러 타입 (예: LLM_CALL_FAILED, CHAT_FAILED)
     * @param model 사용된 모델명
     */
    public void recordError(String errorType, String model) {
        try {
            errorCounter.increment();
            log.warn("AI error recorded: errorType={}, model={}", errorType, model);
        } catch (Exception e) {
            log.error("Failed to record AI error metrics: {}", e.getMessage(), e);
        }
    }
}
