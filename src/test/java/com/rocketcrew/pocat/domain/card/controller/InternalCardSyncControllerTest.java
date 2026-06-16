package com.rocketcrew.pocat.domain.card.controller;

import com.rocketcrew.pocat.domain.card.service.CardSyncService;
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
import org.springframework.core.task.TaskRejectedException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.BDDMockito.willDoNothing;
import static org.mockito.BDDMockito.willThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * InternalCardSyncController 슬라이스 테스트 (RED).
 *
 * <p>대상 엔드포인트: POST /internal/cards/sync
 *
 * <p>{@code /internal/**} 경로는 {@code InternalTokenAuthFilter}에서 X-Internal-Token 헤더를 검증한다.
 * {@code cardSyncService.syncAll()}은 {@code @Async("syncExecutor")}로 비동기 실행되며,
 * 컨트롤러는 호출 즉시(fire-and-forget) 202 Accepted를 반환해야 한다.
 */
@WebMvcTest(controllers = InternalCardSyncController.class)
@Import(SecurityConfig.class)
@TestPropertySource(properties = {"pocat.internal.token=test-token", "cors.allowed-origins=http://localhost:3000"})
@DisplayName("InternalCardSyncController")
class InternalCardSyncControllerTest {

    private static final String URL = "/internal/cards/sync";
    private static final String VALID_TOKEN = "test-token";
    private static final String INVALID_TOKEN = "wrong-token";

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private CardSyncService cardSyncService;

    @MockBean
    private JwtUtil jwtUtil;

    @MockBean
    private StringRedisTemplate stringRedisTemplate;

    @Nested
    @Tag("integration")
    @DisplayName("인증 검증")
    class Authentication {

        @Test
        @DisplayName("X-Internal-Token 헤더가 없으면 401 반환")
        void missingToken_returns401() throws Exception {
            mockMvc.perform(post(URL))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("X-Internal-Token 값이 올바르지 않으면 401 반환")
        void invalidToken_returns401() throws Exception {
            mockMvc.perform(post(URL)
                            .header("X-Internal-Token", INVALID_TOKEN))
                    .andExpect(status().isUnauthorized());
        }
    }

    @Nested
    @DisplayName("정상 요청")
    class Success {

        @Test
        @DisplayName("정상 요청 시 202 ACCEPTED와 ApiResponseDto<Void> success를 반환한다")
        void validRequest_returns202Accepted() throws Exception {
            willDoNothing().given(cardSyncService).syncAll();

            mockMvc.perform(post(URL)
                            .header("X-Internal-Token", VALID_TOKEN))
                    .andExpect(status().isAccepted())
                    .andExpect(jsonPath("$.success").value(true));
        }
    }

    @Nested
    @DisplayName("동기화 중복 요청")
    class AlreadyInProgress {

        @Test
        @DisplayName("syncAll() 호출 시 TaskRejectedException 발생하면 409와 CARD_SYNC_IN_PROGRESS를 반환한다")
        void taskRejected_returns409WithCardSyncInProgress() throws Exception {
            willThrow(new TaskRejectedException("queue full"))
                    .given(cardSyncService).syncAll();

            mockMvc.perform(post(URL)
                            .header("X-Internal-Token", VALID_TOKEN))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("CARD_SYNC_IN_PROGRESS"));
        }
    }
}
