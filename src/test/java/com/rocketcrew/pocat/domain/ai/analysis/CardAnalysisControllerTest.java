package com.rocketcrew.pocat.domain.ai.analysis;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.rocketcrew.pocat.domain.ai.analysis.controller.CardAnalysisController;
import com.rocketcrew.pocat.domain.ai.analysis.dto.CardAnalysisResult;
import com.rocketcrew.pocat.domain.ai.analysis.service.CardAnalysisService;
import com.rocketcrew.pocat.global.exception.common.GlobalExceptionHandler;
import com.rocketcrew.pocat.global.ratelimit.RateLimitProperties;
import com.rocketcrew.pocat.global.ratelimit.RedisRateLimiter;
import com.rocketcrew.pocat.global.security.CustomUserDetails;
import com.rocketcrew.pocat.support.TestCustomUserDetails;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.lenient;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * CardAnalysisController 슬라이스 테스트.
 *
 * <p>CardAnalysisResponse DTO가 적용되어 analyzeCard / reanalyzeCard 응답 JSON에서
 * 내부 필드(analysisModel, promptTokens, completionTokens, analyzedAt)가 제외됩니다.
 * 모든 테스트는 GREEN 상태입니다.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("CardAnalysisController")
class CardAnalysisControllerTest {

    private MockMvc mockMvc;

    @InjectMocks
    private CardAnalysisController controller;

    @Mock
    private CardAnalysisService cardAnalysisService;

    @Mock
    private RedisRateLimiter redisRateLimiter;

    @Mock
    private RateLimitProperties rateLimitProperties;

    private CustomUserDetails userDetails;

    private final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule());

    /** 모든 내부 필드가 채워진 CardAnalysisResult 픽스처 */
    private CardAnalysisResult fullResult;

    @BeforeEach
    void setUp() {
        userDetails = new TestCustomUserDetails(1L, "USER");

        fullResult = new CardAnalysisResult(
                "RISING",
                150_000L,
                "HIGH",
                "최상급 카드 분석 결과",
                List.of("PSA 10 보존 상태"),
                List.of("유동성 위험"),
                List.of("뮤츠", "레어"),
                "gemini-1.5-flash",   // analysisModel — 노출 금지 필드
                100,                   // promptTokens   — 노출 금지 필드
                200,                   // completionTokens — 노출 금지 필드
                LocalDateTime.of(2026, 6, 1, 0, 0) // analyzedAt — 노출 금지 필드
        );

        lenient().when(redisRateLimiter.isAllowed(anyString(), anyInt(), anyLong())).thenReturn(true);
        lenient().when(rateLimitProperties.getAiLimit()).thenReturn(10);
        lenient().when(rateLimitProperties.getAiWindowSeconds()).thenReturn(60L);

        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .setCustomArgumentResolvers(
                        new HandlerMethodArgumentResolver() {
                            @Override
                            public boolean supportsParameter(MethodParameter parameter) {
                                return parameter.hasParameterAnnotation(AuthenticationPrincipal.class);
                            }

                            @Override
                            public Object resolveArgument(MethodParameter parameter,
                                    ModelAndViewContainer mavContainer,
                                    NativeWebRequest webRequest,
                                    WebDataBinderFactory binderFactory) {
                                return userDetails;
                            }
                        })
                .build();
    }

    // ---------------------------------------------------------------
    // GET /api/ai/cards/{cardId}/analysis
    // 응답 JSON에 내부 필드 미포함 검증
    // ---------------------------------------------------------------
    @Nested
    @DisplayName("analyzeCard() 응답 — 내부 필드 미노출")
    class AnalyzeCardResponseFields {

        /**
         * CardAnalysisResponse를 통해 내부 필드(analysisModel, promptTokens,
         * completionTokens, analyzedAt)가 응답에서 제외됩니다.
         */
        @Test
        @DisplayName("GET /api/ai/cards/1/analysis 응답에 analysisModel 필드 없어야 한다")
        void analyzeCard_response_excludes_analysisModel() throws Exception {
            given(cardAnalysisService.analyzeCard(1L)).willReturn(fullResult);

            mockMvc.perform(get("/api/ai/cards/1/analysis")
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.analysisModel").doesNotExist());
        }

        @Test
        @DisplayName("GET /api/ai/cards/1/analysis 응답에 promptTokens 필드 없어야 한다")
        void analyzeCard_response_excludes_promptTokens() throws Exception {
            given(cardAnalysisService.analyzeCard(1L)).willReturn(fullResult);

            mockMvc.perform(get("/api/ai/cards/1/analysis")
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.promptTokens").doesNotExist());
        }

        @Test
        @DisplayName("GET /api/ai/cards/1/analysis 응답에 completionTokens 필드 없어야 한다")
        void analyzeCard_response_excludes_completionTokens() throws Exception {
            given(cardAnalysisService.analyzeCard(1L)).willReturn(fullResult);

            mockMvc.perform(get("/api/ai/cards/1/analysis")
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.completionTokens").doesNotExist());
        }

        @Test
        @DisplayName("GET /api/ai/cards/1/analysis 응답에 analyzedAt 필드 없어야 한다")
        void analyzeCard_response_excludes_analyzedAt() throws Exception {
            given(cardAnalysisService.analyzeCard(1L)).willReturn(fullResult);

            mockMvc.perform(get("/api/ai/cards/1/analysis")
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.analyzedAt").doesNotExist());
        }

        @Test
        @DisplayName("GET /api/ai/cards/1/analysis 응답에 모든 공개 필드 포함 확인 (CardAnalysisResponse 7개 필드)")
        void analyzeCard_response_includes_public_fields() throws Exception {
            given(cardAnalysisService.analyzeCard(1L)).willReturn(fullResult);

            mockMvc.perform(get("/api/ai/cards/1/analysis")
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.priceTrend").exists())
                    .andExpect(jsonPath("$.data.fairValueEstimate").exists())
                    .andExpect(jsonPath("$.data.demandLevel").exists())
                    .andExpect(jsonPath("$.data.summary").exists())
                    .andExpect(jsonPath("$.data.highlights").exists())
                    .andExpect(jsonPath("$.data.riskFactors").exists())
                    .andExpect(jsonPath("$.data.keywords").exists());
        }
    }

    // ---------------------------------------------------------------
    // POST /api/ai/cards/{cardId}/analysis
    // reanalyzeCard 응답도 동일하게 내부 필드 미포함 검증
    // ---------------------------------------------------------------
    @Nested
    @DisplayName("reanalyzeCard() 응답 — 내부 필드 미노출")
    class ReanalyzeCardResponseFields {

        @Test
        @DisplayName("POST /api/ai/cards/1/analysis 응답에 내부 필드 없어야 한다 (analysisModel, promptTokens, completionTokens, analyzedAt)")
        void reanalyzeCard_response_excludes_internal_fields() throws Exception {
            given(cardAnalysisService.reanalyzeCard(1L)).willReturn(fullResult);

            mockMvc.perform(post("/api/ai/cards/1/analysis")
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.analysisModel").doesNotExist())
                    .andExpect(jsonPath("$.data.promptTokens").doesNotExist())
                    .andExpect(jsonPath("$.data.completionTokens").doesNotExist())
                    .andExpect(jsonPath("$.data.analyzedAt").doesNotExist());
        }
    }
}
