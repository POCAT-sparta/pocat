package com.rocketcrew.pocat.global.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class AiUsageMetrics {

    private final MeterRegistry meterRegistry;
    private final Counter promptTokenCounter;
    private final Counter completionTokenCounter;
    private final Timer responseTimer;

    public AiUsageMetrics(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;

        this.promptTokenCounter = Counter.builder("ai.tokens.prompt")
                .description("AI 프롬프트 토큰 누적")
                .register(meterRegistry);

        this.completionTokenCounter = Counter.builder("ai.tokens.completion")
                .description("AI 완료 토큰 누적")
                .register(meterRegistry);

        this.responseTimer = Timer.builder("ai.response.time")
                .description("AI 응답 처리 시간")
                .publishPercentiles(0.5, 0.95, 0.99)
                .register(meterRegistry);
    }

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

    public void recordError(String errorType, String model) {
        try {
            meterRegistry.counter("ai.errors.total",
                    "errorType", errorType != null ? errorType : "UNKNOWN",
                    "model", model != null ? model : "UNKNOWN")
                    .increment();
            log.warn("AI error recorded: errorType={}, model={}", errorType, model);
        } catch (Exception e) {
            log.error("Failed to record AI error metrics: {}", e.getMessage(), e);
        }
    }
}
