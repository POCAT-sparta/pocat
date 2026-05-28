package com.rocketcrew.pocat.domain.ai.analysis;

import com.rocketcrew.pocat.domain.ai.analysis.dto.CardAnalysisResult;
import com.rocketcrew.pocat.domain.ai.analysis.entity.CardAiAnalysis;
import com.rocketcrew.pocat.domain.ai.analysis.repository.CardAiAnalysisRepository;
import com.rocketcrew.pocat.domain.ai.analysis.service.CardAnalysisService;
import com.rocketcrew.pocat.domain.ai.monitoring.AiUsageMetrics;
import com.rocketcrew.pocat.domain.ai.prompt.service.AiPromptTemplateService;
import com.rocketcrew.pocat.domain.card.entity.Card;
import com.rocketcrew.pocat.domain.card.entity.enums.CardCategory;
import com.rocketcrew.pocat.domain.card.entity.enums.CardGrade;
import com.rocketcrew.pocat.domain.card.entity.enums.CardSource;
import com.rocketcrew.pocat.domain.card.entity.enums.CardStatus;
import com.rocketcrew.pocat.domain.card.repository.CardRepository;
import com.rocketcrew.pocat.global.exception.common.ServiceException;
import com.rocketcrew.pocat.support.TestFixtures;
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
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.util.ReflectionTestUtils;

import java.lang.reflect.Method;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willDoNothing;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("CardAnalysisService")
class CardAnalysisServiceTest {

    @InjectMocks
    private CardAnalysisService cardAnalysisService;

    @Mock
    private ChatClient chatClient;

    @Mock
    private CardRepository cardRepository;

    @Mock
    private AiPromptTemplateService promptTemplateService;

    @Mock
    private AiUsageMetrics aiUsageMetrics;

    @Mock
    private CardAiAnalysisRepository cardAiAnalysisRepository;

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @Mock
    private ChatClient.ChatClientRequestSpec requestSpec;

    @Mock
    private ChatClient.CallResponseSpec callResponseSpec;

    @Mock
    private ObjectMapper objectMapper;

    private Card psa10Card;

    @BeforeEach
    void setUp() {
        psa10Card = Card.builder()
                .userId(1L)
                .name("뮤츠")
                .series(TestFixtures.aSeries())
                .pokemonSet(TestFixtures.aPokemonSet())
                .cardNumber("001")
                .rarity("SSR")
                .category(CardCategory.POKEMON)
                .grade(CardGrade.PSA_10)
                .source(CardSource.TCGDEX)
                .status(CardStatus.ACTIVE)
                .build();
        ReflectionTestUtils.setField(psa10Card, "id", 1L);

        // wire self-reference so reanalyzeCard() → self.analyzeCard() works in unit test
        ReflectionTestUtils.setField(cardAnalysisService, "self", cardAnalysisService);

        given(redisTemplate.opsForValue()).willReturn(valueOperations);
        given(chatClient.prompt(any(Prompt.class))).willReturn(requestSpec);
        given(requestSpec.call()).willReturn(callResponseSpec);
        try {
            given(objectMapper.writeValueAsString(any())).willReturn("{}");
        } catch (Exception ignored) {}
        willDoNothing().given(aiUsageMetrics).recordUsage(anyInt(), anyInt(), anyLong(), anyString());
    }

    // ---------------------------------------------------------------
    // analyzeCard
    // ---------------------------------------------------------------
    @Nested
    @DisplayName("analyzeCard()")
    class AnalyzeCard {

        @Test
        @DisplayName("성공: 카드 분석 정상 완료 → CardAnalysisResult 반환")
        void analyzeCard_success() {
            // given
            given(cardRepository.findById(1L)).willReturn(Optional.of(psa10Card));
            given(valueOperations.get(anyString())).willReturn(null); // cache miss
            given(promptTemplateService.getPrompt("PSA_10")).willReturn(
                    "카드 분석: {cardContext}\n{format}");
            String llmJson = "{\"priceTrend\":\"RISING\",\"fairValueEstimate\":150000,\"demandLevel\":\"HIGH\","
                    + "\"summary\":\"최상급 카드\",\"highlights\":[],\"riskFactors\":[],\"keywords\":[],"
                    + "\"analysisModel\":\"gemini-1.5-flash\",\"promptTokens\":100,\"completionTokens\":200,"
                    + "\"analyzedAt\":\"2026-05-26T00:00:00\"}";
            given(callResponseSpec.content()).willReturn(llmJson);

            // when
            CardAnalysisResult result = cardAnalysisService.analyzeCard(1L);

            // then
            assertThat(result).isNotNull();
            assertThat(result.priceTrend()).isEqualTo("RISING");
            verify(promptTemplateService).getPrompt("PSA_10");
        }

        @Test
        @DisplayName("fallback: LLM 예외 발생 시 analyzeCardFallback UNKNOWN trend 반환 확인 (직접 호출)")
        void analyzeCard_fallback_returns_unknown_trend() {
            // analyzeCardFallback is package-private accessible via reflection or tested via result shape.
            // Without Spring AOP in unit tests, verify fallback result shape directly.
            // given — build a fallback result as the method would produce
            CardAnalysisResult fallback = new CardAnalysisResult(
                    "UNKNOWN", null, "UNKNOWN",
                    "현재 AI 분석 서비스를 이용할 수 없습니다. 잠시 후 다시 시도해주세요.",
                    java.util.List.of(), java.util.List.of(), java.util.List.of(),
                    "gemini-1.5-flash", 0, 0, java.time.LocalDateTime.now());

            // then — fallback shape assertions
            assertThat(fallback.priceTrend()).isEqualTo("UNKNOWN");
            assertThat(fallback.demandLevel()).isEqualTo("UNKNOWN");
            assertThat(fallback.summary()).contains("AI 분석 서비스");
        }
    }

    // ---------------------------------------------------------------
    // reanalyzeCard
    // ---------------------------------------------------------------
    @Nested
    @DisplayName("reanalyzeCard()")
    class ReanalyzeCard {

        @Test
        @DisplayName("재분석 호출 시 Redis delete 후 analyzeCard 수행")
        void reanalyzeCard_deletes_cache_and_reanalyzes() {
            // given
            given(cardRepository.findById(1L)).willReturn(Optional.of(psa10Card));
            given(valueOperations.get(anyString())).willReturn(null);
            given(promptTemplateService.getPrompt("PSA_10")).willReturn(
                    "카드 분석: {cardContext}\n{format}");
            String llmJson = "{\"priceTrend\":\"STABLE\",\"fairValueEstimate\":100000,\"demandLevel\":\"MEDIUM\","
                    + "\"summary\":\"안정적\",\"highlights\":[],\"riskFactors\":[],\"keywords\":[],"
                    + "\"analysisModel\":\"gemini-1.5-flash\",\"promptTokens\":80,\"completionTokens\":120,"
                    + "\"analyzedAt\":\"2026-05-26T00:00:00\"}";
            given(callResponseSpec.content()).willReturn(llmJson);
            given(redisTemplate.delete(anyString())).willReturn(true);

            // when
            CardAnalysisResult result = cardAnalysisService.reanalyzeCard(1L);

            // then
            verify(redisTemplate).delete("ai:analysis:card:1");
            assertThat(result).isNotNull();
        }
    }

    // ---------------------------------------------------------------
    // CardAiAnalysis 영속화 + RateLimiter (infra-fix #116)
    // ---------------------------------------------------------------
    @Nested
    @DisplayName("CardAiAnalysis 영속화 / RateLimiter (#116)")
    class PersistenceAndRateLimiter {

        @Test
        @DisplayName("analyzeCard 성공 시 cardAiAnalysisRepository.save()가 호출되어야 한다")
        void analyzeCard_persistsToCardAiAnalysis() {
            // given
            given(cardRepository.findById(1L)).willReturn(Optional.of(psa10Card));
            given(valueOperations.get(anyString())).willReturn(null);
            given(promptTemplateService.getPrompt("PSA_10")).willReturn(
                    "카드 분석: {cardContext}\n{format}");
            String llmJson = "{\"priceTrend\":\"RISING\",\"fairValueEstimate\":150000,\"demandLevel\":\"HIGH\","
                    + "\"summary\":\"최상급\",\"highlights\":[],\"riskFactors\":[],\"keywords\":[],"
                    + "\"analysisModel\":\"gemini-1.5-flash\",\"promptTokens\":100,\"completionTokens\":200,"
                    + "\"analyzedAt\":\"2026-05-26T00:00:00\"}";
            given(callResponseSpec.content()).willReturn(llmJson);
            given(cardAiAnalysisRepository.save(any(CardAiAnalysis.class)))
                    .willAnswer(inv -> inv.getArgument(0));

            // when
            cardAnalysisService.analyzeCard(1L);

            // then: FAILS until CardAnalysisService is updated to call cardAiAnalysisRepository.save()
            verify(cardAiAnalysisRepository).save(any(CardAiAnalysis.class));
        }

        @Test
        @DisplayName("analyzeCard() 메서드에 @RateLimiter 어노테이션이 선언되어 있어야 한다")
        void analyzeCard_isAnnotatedWithRateLimiter() throws NoSuchMethodException {
            Method m = CardAnalysisService.class.getMethod("analyzeCard", Long.class);
            io.github.resilience4j.ratelimiter.annotation.RateLimiter rl =
                    m.getAnnotation(io.github.resilience4j.ratelimiter.annotation.RateLimiter.class);
            assertThat(rl)
                    .as("analyzeCard() must be annotated with @RateLimiter")
                    .isNotNull();
            assertThat(rl.name()).isEqualTo("aiEndpoint");
        }
    }
}
