package com.rocketcrew.pocat.domain.ai.analysis.service;

import com.rocketcrew.pocat.domain.ai.analysis.dto.CardAnalysisResult;
import com.rocketcrew.pocat.domain.ai.monitoring.AiUsageMetrics;
import com.rocketcrew.pocat.domain.ai.prompt.service.AiPromptTemplateService;
import com.rocketcrew.pocat.domain.card.entity.Card;
import com.rocketcrew.pocat.domain.card.repository.CardRepository;
import com.rocketcrew.pocat.global.exception.common.ErrorCode;
import com.rocketcrew.pocat.global.exception.common.ServiceException;
import com.rocketcrew.pocat.global.exception.domain.CardException;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CardAnalysisService {

    private final ChatClient chatClient;
    private final CardRepository cardRepository;
    private final AiPromptTemplateService promptTemplateService;
    private final AiUsageMetrics aiUsageMetrics;
    private final StringRedisTemplate redisTemplate;

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
    @CircuitBreaker(name = "aiService", fallbackMethod = "analyzeCardFallback")
    @Cacheable(value = "cardAnalysis", key = "#cardId", unless = "#result == null")
    public CardAnalysisResult analyzeCard(Long cardId) {
        log.info("Starting card analysis for cardId: {}", cardId);

        // 카드 존재 확인
        Card card = cardRepository.findById(cardId)
                .orElseThrow(() -> new CardException(ErrorCode.CARD_NOT_FOUND));

        // 캐시 확인
        String cacheKey = CACHE_KEY_PREFIX + cardId;
        String cachedResult = redisTemplate.opsForValue().get(cacheKey);
        if (cachedResult != null) {
            log.debug("Cache hit for cardId: {}", cardId);
            return deserializeAnalysisResult(cachedResult);
        }

        // 등급별 프롬프트 획득
        String prompt = promptTemplateService.getPrompt(card.getGrade().toString());

        // 분석 대상 카드 정보 구성
        String cardContext = buildCardContext(card);

        // LLM 호출
        CardAnalysisResult result = callLlmForAnalysis(cardContext, prompt);

        // 캐시 저장 (TTL 24시간)
        String serializedResult = serializeAnalysisResult(result);
        redisTemplate.opsForValue().set(cacheKey, serializedResult, CACHE_TTL_HOURS, TimeUnit.HOURS);

        // 메트릭 기록
        if (result.promptTokens() != null && result.completionTokens() != null) {
            aiUsageMetrics.recordUsage(
                    result.promptTokens(),
                    result.completionTokens(),
                    0L,
                    FALLBACK_MODEL
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
    @CacheEvict(value = "cardAnalysis", key = "#cardId")
    @Transactional
    public CardAnalysisResult reanalyzeCard(Long cardId) {
        log.info("Forcing card reanalysis for cardId: {}", cardId);

        // Redis 캐시도 제거
        String cacheKey = CACHE_KEY_PREFIX + cardId;
        redisTemplate.delete(cacheKey);

        // 재분석 수행
        return analyzeCard(cardId);
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
            log.info("Returning cached result from fallback for cardId: {}", cardId);
            return deserializeAnalysisResult(cachedResult);
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

    /**
     * LLM 호출 및 응답 파싱.
     */
    private CardAnalysisResult callLlmForAnalysis(String cardContext, String promptTemplate) {
        try {
            BeanOutputConverter<CardAnalysisResult> outputConverter =
                    new BeanOutputConverter<>(CardAnalysisResult.class);

            PromptTemplate template = new PromptTemplate(promptTemplate);
            Prompt prompt = template.create(Map.of(
                    "cardContext", cardContext,
                    "format", outputConverter.getFormat()
            ));

            String response = chatClient.prompt(prompt).call().content();

            log.debug("LLM response received for card analysis");
            return outputConverter.convert(response);
        } catch (Exception e) {
            log.error("LLM call failed: {}", e.getMessage(), e);
            aiUsageMetrics.recordError("LLM_CALL_FAILED", FALLBACK_MODEL);
            throw new ServiceException(ErrorCode.INTERNAL_SERVER_ERROR);
        }
    }

    /**
     * 카드 정보를 분석 대상 컨텍스트로 구성.
     */
    private String buildCardContext(Card card) {
        return String.format(
                "카드 이름: %s\n등급: %s\n시리즈: %s\n세트: %s\nURL: %s\n레어도: %s",
                card.getName(),
                card.getGrade().toString(),
                card.getSeries(),
                card.getSetName(),
                card.getImageUrl() != null ? card.getImageUrl() : "N/A",
                card.getRarity()
        );
    }

    /**
     * 분석 결과 직렬화 (Redis 저장용).
     */
    private String serializeAnalysisResult(CardAnalysisResult result) {
        // 실제 구현에서는 JSON 직렬화 사용 (예: ObjectMapper)
        return result.toString();
    }

    /**
     * 분석 결과 역직렬화 (Redis 조회용).
     */
    private CardAnalysisResult deserializeAnalysisResult(String cached) {
        // 실제 구현에서는 JSON 역직렬화 사용
        // 여기서는 로그만 처리
        log.debug("Deserialized analysis result from cache");
        return null; // DB 에이전트에서 처리 요청 필요
    }
}
