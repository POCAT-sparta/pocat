package com.rocketcrew.pocat.domain.ai.analysis.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rocketcrew.pocat.domain.ai.analysis.dto.CardAnalysisResult;
import com.rocketcrew.pocat.domain.ai.analysis.entity.CardAiAnalysis;
import com.rocketcrew.pocat.domain.ai.analysis.repository.CardAiAnalysisRepository;
import com.rocketcrew.pocat.global.metrics.AiUsageMetrics;
import com.rocketcrew.pocat.domain.ai.prompt.service.AiPromptTemplateService;
import com.rocketcrew.pocat.domain.card.entity.Card;
import com.rocketcrew.pocat.domain.card.repository.CardRepository;
import com.rocketcrew.pocat.global.exception.common.ErrorCode;
import com.rocketcrew.pocat.global.exception.common.ServiceException;
import com.rocketcrew.pocat.global.exception.domain.CardException;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.ratelimiter.annotation.RateLimiter;
import io.github.resilience4j.ratelimiter.RequestNotPermitted;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.concurrent.TimeUnit;

/**
 * 카드 AI 분석 오케스트레이션.
 * 캐시 조회/저장, 컨텍스트 구성({@link CardContextBuilder}) 및 LLM 호출({@link CardAnalysisLlmClient})
 * 위임, 결과 영속화·메트릭 기록, 장애 시 fallback을 담당한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CardAnalysisService {

    private final CardRepository cardRepository;
    private final AiPromptTemplateService promptTemplateService;
    private final AiUsageMetrics aiUsageMetrics;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final CardAiAnalysisRepository cardAiAnalysisRepository;
    private final CardContextBuilder cardContextBuilder;
    private final CardAnalysisLlmClient cardAnalysisLlmClient;

    @Lazy
    @Autowired
    private CardAnalysisService self;

    private static final String CACHE_KEY_PREFIX = "ai:analysis:card:";
    private static final long CACHE_TTL_HOURS = 24;
    private static final String FALLBACK_MODEL = "gemini-1.5-flash";

    /**
     * 카드 AI 분석 수행 (Circuit Breaker 포함).
     * Redis 캐시 우선 확인, 없으면 LLM 호출 후 캐시 저장.
     *
     * @param cardId 카드 ID
     * @return 분석 결과
     */
    @RateLimiter(name = "aiEndpoint", fallbackMethod = "analyzeCardRateLimitFallback")
    @CircuitBreaker(name = "aiService", fallbackMethod = "analyzeCardFallback")
    @Cacheable(value = "cardAnalysis", key = "#cardId", unless = "#result == null")
    @Transactional
    public CardAnalysisResult analyzeCard(Long cardId) {
        log.info("Starting card analysis for cardId: {}", cardId);

        // 카드 존재 확인
        Card card = cardRepository.findById(cardId)
                .orElseThrow(() -> new CardException(ErrorCode.CARD_NOT_FOUND));

        // 캐시 확인
        String cacheKey = CACHE_KEY_PREFIX + cardId;
        String cachedResult = redisTemplate.opsForValue().get(cacheKey);
        if (cachedResult != null) {
            CardAnalysisResult cached = deserializeAnalysisResult(cachedResult);
            if (cached != null) {
                log.debug("Cache hit for cardId: {}", cardId);
                return cached;
            }
            log.warn("Corrupted cache entry for cardId: {}, falling back to LLM", cardId);
            redisTemplate.delete(cacheKey);
        }

        // 등급별 프롬프트 획득
        String prompt = promptTemplateService.getPrompt(card.getGrade().toString());

        // 분석 대상 카드 정보 구성 (내부 정보 + TCGdex 실측 데이터 + 환율)
        String cardContext = cardContextBuilder.build(card);

        // LLM 호출
        long startMs = System.currentTimeMillis();
        CardAnalysisResult result = cardAnalysisLlmClient.analyze(cardContext, prompt);
        long latencyMs = System.currentTimeMillis() - startMs;

        // 캐시 저장 (TTL 24시간)
        String serializedResult = serializeAnalysisResult(result);
        redisTemplate.opsForValue().set(cacheKey, serializedResult, CACHE_TTL_HOURS, TimeUnit.HOURS);

        // DB 영속화
        try {
            cardAiAnalysisRepository.save(CardAiAnalysis.builder()
                    .cardId(cardId)
                    .priceTrend(result.priceTrend() != null ? result.priceTrend() : "UNKNOWN")
                    .fairValueEstimate(result.fairValueEstimate())
                    .demandLevel(result.demandLevel() != null ? result.demandLevel() : "UNKNOWN")
                    .summary(result.summary())
                    .highlights(serializeList(result.highlights()))
                    .riskFactors(serializeList(result.riskFactors()))
                    .keywords(serializeList(result.keywords()))
                    .analysisModel(result.analysisModel() != null ? result.analysisModel() : FALLBACK_MODEL)
                    .promptTokens(result.promptTokens())
                    .completionTokens(result.completionTokens())
                    .build());
        } catch (Exception e) {
            log.warn("Failed to persist CardAiAnalysis for cardId={}: {}", cardId, e.getMessage());
        }

        // 메트릭 기록
        if (result.promptTokens() != null && result.completionTokens() != null) {
            aiUsageMetrics.recordUsage(
                    result.promptTokens(),
                    result.completionTokens(),
                    latencyMs,
                    result.analysisModel() != null ? result.analysisModel() : FALLBACK_MODEL
            );
        }

        log.info("Card analysis completed for cardId: {}", cardId);
        return result;
    }

    /**
     * 카드 분석 강제 재생성 (캐시 무효화 후 재분석).
     *
     * @param cardId 카드 ID
     * @return 새로운 분석 결과
     */
    @CircuitBreaker(name = "aiService", fallbackMethod = "analyzeCardFallback")
    @CacheEvict(value = "cardAnalysis", key = "#cardId", beforeInvocation = true)
    @Transactional
    public CardAnalysisResult reanalyzeCard(Long cardId) {
        log.info("Forcing card reanalysis for cardId: {}", cardId);

        // Redis 캐시도 제거
        String cacheKey = CACHE_KEY_PREFIX + cardId;
        redisTemplate.delete(cacheKey);

        // 재분석 수행
        return self.analyzeCard(cardId);
    }

    private String serializeList(java.util.List<String> list) {
        if (list == null || list.isEmpty()) return null;
        try {
            return objectMapper.writeValueAsString(list);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Circuit Breaker fallback: 이전 캐시 결과 또는 에러 메시지 반환.
     */
    private CardAnalysisResult analyzeCardFallback(Long cardId, Throwable t) {
        log.warn("Circuit breaker triggered for cardId: {} - {}", cardId, t.getMessage());

        // Fallback 1: Redis 캐시에서 이전 결과 조회
        String cacheKey = CACHE_KEY_PREFIX + cardId;
        String cachedResult = redisTemplate.opsForValue().get(cacheKey);
        if (cachedResult != null) {
            CardAnalysisResult cached = deserializeAnalysisResult(cachedResult);
            if (cached != null) {
                log.info("Returning cached result from fallback for cardId: {}", cardId);
                return cached;
            }
            log.warn("Corrupted cache in fallback for cardId: {}, deleting entry", cardId);
            redisTemplate.delete(cacheKey);
        }

        // Fallback 2: 기본 응답
        log.info("No cache available, returning default fallback for cardId: {}", cardId);
        return new CardAnalysisResult(
                "UNKNOWN",
                null,
                "UNKNOWN",
                "현재 AI 분석 서비스를 이용할 수 없습니다. 잠시 후 다시 시도해주세요.",
                java.util.List.of(),
                java.util.List.of(),
                java.util.List.of(),
                FALLBACK_MODEL,
                0,
                0,
                LocalDateTime.now()
        );
    }

    private String serializeAnalysisResult(CardAnalysisResult result) {
        try {
            return objectMapper.writeValueAsString(result);
        } catch (Exception e) {
            log.warn("Failed to serialize analysis result: {}", e.getMessage());
            throw new ServiceException(ErrorCode.INTERNAL_SERVER_ERROR, e);
        }
    }

    private CardAnalysisResult deserializeAnalysisResult(String cached) {
        try {
            return objectMapper.readValue(cached, CardAnalysisResult.class);
        } catch (Exception e) {
            log.warn("Failed to deserialize cached result, returning null: {}", e.getMessage());
            return null;
        }
    }

    private CardAnalysisResult analyzeCardRateLimitFallback(Long cardId, RequestNotPermitted ex) {
        log.warn("Rate limit exceeded for analyzeCard cardId={}", cardId);
        throw new ServiceException(ErrorCode.AI_RATE_LIMITED);
    }

}
