package com.rocketcrew.pocat.domain.ai.rag.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rocketcrew.pocat.domain.ai.rag.dto.ReindexChunkRequest;
import com.rocketcrew.pocat.domain.ai.rag.dto.ReindexChunkResponse;
import com.rocketcrew.pocat.domain.ai.rag.service.AiReindexChunkService;
import com.rocketcrew.pocat.global.config.SecurityConfig;
import com.rocketcrew.pocat.global.security.JwtUtil;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * InternalAiController 슬라이스 테스트 (RED).
 *
 * <p>POST /internal/ai/reindex-cards 엔드포인트:
 * <ul>
 *   <li>X-Internal-Token 헤더 검증 (401)</li>
 *   <li>Idempotency-Key 헤더 필수 (400)</li>
 *   <li>정상 요청 시 ApiResponseDto&lt;ReindexChunkResponse&gt; 응답 (200)</li>
 * </ul>
 */
@WebMvcTest(controllers = InternalAiController.class)
@Import(SecurityConfig.class)
@TestPropertySource(properties = {"pocat.internal.token=test-token", "cors.allowed-origins=http://localhost:3000"})
@DisplayName("InternalAiController")
class InternalAiControllerTest {

    private static final String URL = "/internal/ai/reindex-cards";
    private static final String VALID_TOKEN = "test-token";
    private static final String INVALID_TOKEN = "wrong-token";
    private static final String IDEMPOTENCY_KEY = "idem-key-001";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private AiReindexChunkService aiReindexChunkService;

    @MockBean
    private JwtUtil jwtUtil;

    @MockBean
    private StringRedisTemplate stringRedisTemplate;

    private String requestBody() throws Exception {
        return objectMapper.writeValueAsString(new ReindexChunkRequest(List.of(1L, 2L, 3L)));
    }

    @Nested
    @Tag("integration")
    @DisplayName("인증 검증")
    class Authentication {

        @Test
        @DisplayName("X-Internal-Token 헤더가 없으면 401 반환")
        void missingToken_returns401() throws Exception {
            mockMvc.perform(post(URL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .header("Idempotency-Key", IDEMPOTENCY_KEY)
                            .content(requestBody()))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("X-Internal-Token 값이 올바르지 않으면 401 반환")
        void invalidToken_returns401() throws Exception {
            mockMvc.perform(post(URL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .header("X-Internal-Token", INVALID_TOKEN)
                            .header("Idempotency-Key", IDEMPOTENCY_KEY)
                            .content(requestBody()))
                    .andExpect(status().isUnauthorized());
        }
    }

    @Nested
    @DisplayName("요청 검증")
    class RequestValidation {

        @Test
        @DisplayName("Idempotency-Key 헤더가 없으면 400 반환")
        void missingIdempotencyKey_returns400() throws Exception {
            mockMvc.perform(post(URL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .header("X-Internal-Token", VALID_TOKEN)
                            .content(requestBody()))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("cardIds가 비어있으면 400 반환")
        void emptyCardIds_returns400() throws Exception {
            String body = objectMapper.writeValueAsString(new ReindexChunkRequest(List.of()));

            mockMvc.perform(post(URL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .header("X-Internal-Token", VALID_TOKEN)
                            .header("Idempotency-Key", IDEMPOTENCY_KEY)
                            .content(body))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("cardIds가 100개를 초과하면 400 반환")
        void oversizedCardIds_returns400() throws Exception {
            List<Long> cardIds = java.util.stream.LongStream.rangeClosed(1, 101)
                    .boxed()
                    .toList();
            String body = objectMapper.writeValueAsString(new ReindexChunkRequest(cardIds));

            mockMvc.perform(post(URL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .header("X-Internal-Token", VALID_TOKEN)
                            .header("Idempotency-Key", IDEMPOTENCY_KEY)
                            .content(body))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("요청 본문이 null이면 400 반환")
        void nullRequestBody_returns400() throws Exception {
            mockMvc.perform(post(URL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .header("X-Internal-Token", VALID_TOKEN)
                            .header("Idempotency-Key", IDEMPOTENCY_KEY)
                            .content("null"))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("cardIds에 null 요소가 있으면 400 반환")
        void nullElementInCardIds_returns400() throws Exception {
            String body = objectMapper.writeValueAsString(new ReindexChunkRequest(java.util.Arrays.asList(1L, null, 3L)));

            mockMvc.perform(post(URL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .header("X-Internal-Token", VALID_TOKEN)
                            .header("Idempotency-Key", IDEMPOTENCY_KEY)
                            .content(body))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("cardIds에 0이 있으면 400 반환")
        void zeroValueInCardIds_returns400() throws Exception {
            String body = objectMapper.writeValueAsString(new ReindexChunkRequest(List.of(0L, 1L, 2L)));

            mockMvc.perform(post(URL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .header("X-Internal-Token", VALID_TOKEN)
                            .header("Idempotency-Key", IDEMPOTENCY_KEY)
                            .content(body))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("cardIds에 음수가 있으면 400 반환")
        void negativeValueInCardIds_returns400() throws Exception {
            String body = objectMapper.writeValueAsString(new ReindexChunkRequest(List.of(-1L, 1L, 2L)));

            mockMvc.perform(post(URL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .header("X-Internal-Token", VALID_TOKEN)
                            .header("Idempotency-Key", IDEMPOTENCY_KEY)
                            .content(body))
                    .andExpect(status().isBadRequest());
        }
    }

    @Nested
    @DisplayName("정상 요청")
    class Success {

        @Test
        @DisplayName("유효한 토큰과 Idempotency-Key로 요청 시 200과 ApiResponseDto<ReindexChunkResponse>를 반환한다")
        void validRequest_returns200WithReindexChunkResponse() throws Exception {
            // given
            ReindexChunkResponse result = new ReindexChunkResponse(3, 1, 2, 0, false);
            given(aiReindexChunkService.reindex(any())).willReturn(result);

            // when & then
            mockMvc.perform(post(URL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .header("X-Internal-Token", VALID_TOKEN)
                            .header("Idempotency-Key", IDEMPOTENCY_KEY)
                            .content(requestBody()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.processedCount").value(3))
                    .andExpect(jsonPath("$.data.skippedCount").value(1))
                    .andExpect(jsonPath("$.data.indexedCount").value(2))
                    .andExpect(jsonPath("$.data.failedCount").value(0))
                    .andExpect(jsonPath("$.data.rateLimited").value(false));
        }

        @Test
        @DisplayName("rate limit에 도달한 경우 rateLimited=true를 포함해 200을 반환한다")
        void rateLimited_returns200WithRateLimitedTrue() throws Exception {
            // given
            ReindexChunkResponse result = new ReindexChunkResponse(3, 0, 1, 0, true);
            given(aiReindexChunkService.reindex(any())).willReturn(result);

            // when & then
            mockMvc.perform(post(URL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .header("X-Internal-Token", VALID_TOKEN)
                            .header("Idempotency-Key", IDEMPOTENCY_KEY)
                            .content(requestBody()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.rateLimited").value(true));
        }
    }
}
