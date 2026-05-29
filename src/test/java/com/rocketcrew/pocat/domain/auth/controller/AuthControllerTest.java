package com.rocketcrew.pocat.domain.auth.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rocketcrew.pocat.domain.auth.dto.request.LoginRequest;
import com.rocketcrew.pocat.domain.auth.dto.request.ReissueRequest;
import com.rocketcrew.pocat.domain.auth.dto.request.SignupRequest;
import com.rocketcrew.pocat.domain.auth.dto.response.SignupResponse;
import com.rocketcrew.pocat.domain.auth.dto.response.TokenResponse;
import com.rocketcrew.pocat.domain.auth.service.AuthService;
import com.rocketcrew.pocat.global.exception.common.ErrorCode;
import com.rocketcrew.pocat.global.exception.common.GlobalExceptionHandler;
import com.rocketcrew.pocat.global.exception.domain.AuthException;
import com.rocketcrew.pocat.global.ratelimit.RateLimitProperties;
import com.rocketcrew.pocat.global.ratelimit.RedisRateLimiter;
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
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.ArgumentMatchers.eq;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AuthControllerTest {

    private MockMvc mockMvc;

    @InjectMocks
    private AuthController authController;

    @Mock
    private AuthService authService;

    @Mock
    private RedisRateLimiter redisRateLimiter;

    @Mock
    private RateLimitProperties rateLimitProperties;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        given(redisRateLimiter.isAllowed(anyString(), anyInt(), anyLong())).willReturn(true);
        mockMvc = MockMvcBuilders.standaloneSetup(authController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Nested
    @DisplayName("POST /api/v1/auth/signup")
    class Signup {

        @Test
        @DisplayName("성공: 201 Created 반환")
        void success_201() throws Exception {
            // given
            SignupRequest request = new SignupRequest(
                    "test@example.com", "Password1!", "tester", "010-1234-5678");
            SignupResponse response = new SignupResponse(1L, "test@example.com", "tester");
            given(authService.signup(any(SignupRequest.class))).willReturn(response);

            // when & then
            mockMvc.perform(post("/api/v1/auth/signup")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isCreated());
        }

        @Test
        @DisplayName("실패: 이미 존재하는 이메일 → 409 Conflict")
        void fail_409_emailConflict() throws Exception {
            // given
            SignupRequest request = new SignupRequest(
                    "test@example.com", "Password1!", "tester", "010-1234-5678");
            given(authService.signup(any(SignupRequest.class)))
                    .willThrow(new AuthException(ErrorCode.EMAIL_ALREADY_EXISTS));

            // when & then
            mockMvc.perform(post("/api/v1/auth/signup")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isConflict());
        }
    }

    @Nested
    @DisplayName("POST /api/v1/auth/login")
    class Login {

        @Test
        @DisplayName("성공: 200 OK 및 토큰 반환")
        void success_200() throws Exception {
            // given
            LoginRequest request = new LoginRequest("test@example.com", "Password1!");
            TokenResponse response = new TokenResponse("accessToken", "refreshToken");
            given(authService.login(any(LoginRequest.class))).willReturn(response);

            // when & then
            mockMvc.perform(post("/api/v1/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("실패: 잘못된 자격증명 → 400 Bad Request")
        void fail_400_wrongCredentials() throws Exception {
            // given
            LoginRequest request = new LoginRequest("test@example.com", "WrongPassword1!");
            given(authService.login(any(LoginRequest.class)))
                    .willThrow(new AuthException(ErrorCode.USER_INFO_MISMATCH));

            // when & then
            mockMvc.perform(post("/api/v1/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest());
        }
    }

    @Nested
    @DisplayName("POST /api/v1/auth/reissue")
    class Reissue {

        @Test
        @DisplayName("성공: 200 OK 및 새 토큰 반환")
        void success_200() throws Exception {
            // given
            ReissueRequest request = new ReissueRequest("validRefreshToken");
            TokenResponse response = new TokenResponse("newAccessToken", "newRefreshToken");
            given(authService.reissue(any(ReissueRequest.class))).willReturn(response);

            // when & then
            mockMvc.perform(post("/api/v1/auth/reissue")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("실패: 유효하지 않은 토큰 → 401 Unauthorized")
        void fail_401_invalidToken() throws Exception {
            // given
            ReissueRequest request = new ReissueRequest("invalidToken");
            given(authService.reissue(any(ReissueRequest.class)))
                    .willThrow(new AuthException(ErrorCode.INVALID_REFRESH_TOKEN));

            // when & then
            mockMvc.perform(post("/api/v1/auth/reissue")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isUnauthorized());
        }
    }

    @Nested
    @DisplayName("POST /api/v1/auth/logout")
    class Logout {

        @Test
        @DisplayName("성공: 유효한 Bearer 토큰 → 200 OK")
        void success_200() throws Exception {
            // given
            doNothing().when(authService).logout(anyString());

            // when & then
            mockMvc.perform(post("/api/v1/auth/logout")
                            .header("Authorization", "Bearer validAccessToken"))
                    .andExpect(status().isOk());

            verify(authService).logout(eq("validAccessToken"));
        }

        @Test
        @DisplayName("실패: Authorization 헤더 없음 → 401 Unauthorized")
        void fail_401_missingHeader() throws Exception {
            // when & then
            mockMvc.perform(post("/api/v1/auth/logout"))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("실패: Bearer 접두사 없음 → 401 Unauthorized")
        void fail_401_noBearerPrefix() throws Exception {
            // when & then
            mockMvc.perform(post("/api/v1/auth/logout")
                            .header("Authorization", "invalidTokenWithoutBearer"))
                    .andExpect(status().isUnauthorized());
        }
    }
}
