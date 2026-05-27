package com.rocketcrew.pocat.domain.auth.service;

import com.rocketcrew.pocat.domain.auth.dto.request.LoginRequest;
import com.rocketcrew.pocat.domain.auth.dto.request.ReissueRequest;
import com.rocketcrew.pocat.domain.auth.dto.request.SignupRequest;
import com.rocketcrew.pocat.domain.auth.dto.response.SignupResponse;
import com.rocketcrew.pocat.domain.auth.dto.response.TokenResponse;
import com.rocketcrew.pocat.domain.user.entity.User;
import com.rocketcrew.pocat.domain.user.enums.UserRole;
import com.rocketcrew.pocat.domain.user.repository.UserRepository;
import com.rocketcrew.pocat.global.exception.common.ErrorCode;
import com.rocketcrew.pocat.global.exception.domain.AuthException;
import com.rocketcrew.pocat.global.security.JwtUtil;
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
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AuthServiceTest {

    @InjectMocks
    private AuthService authService;

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtUtil jwtUtil;

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOps;

    private User testUser;

    @BeforeEach
    void setUp() {
        given(redisTemplate.opsForValue()).willReturn(valueOps);

        testUser = User.builder()
                .email("test@example.com")
                .password("encodedPassword")
                .nickname("tester")
                .phone("010-1234-5678")
                .userRole(UserRole.USER)
                .build();
        ReflectionTestUtils.setField(testUser, "id", 1L);
    }

    @Nested
    @DisplayName("signup()")
    class Signup {

        @Test
        @DisplayName("성공: 신규 이메일로 회원가입")
        void success() {
            // given
            SignupRequest request = new SignupRequest(
                    "test@example.com", "Password1!", "tester", "010-1234-5678");
            given(userRepository.existsByEmail("test@example.com")).willReturn(false);
            given(passwordEncoder.encode("Password1!")).willReturn("encodedPassword");
            given(userRepository.save(any(User.class))).willAnswer(inv -> {
                User u = inv.getArgument(0);
                ReflectionTestUtils.setField(u, "id", 1L);
                return u;
            });

            // when
            SignupResponse response = authService.signup(request);

            // then
            assertThat(response.email()).isEqualTo("test@example.com");
            assertThat(response.nickname()).isEqualTo("tester");
            verify(userRepository).save(any(User.class));
        }

        @Test
        @DisplayName("실패: 이미 존재하는 이메일 → EMAIL_ALREADY_EXISTS")
        void fail_duplicateEmail() {
            // given
            SignupRequest request = new SignupRequest(
                    "test@example.com", "Password1!", "tester", "010-1234-5678");
            given(userRepository.existsByEmail("test@example.com")).willReturn(true);

            // when & then
            assertThatThrownBy(() -> authService.signup(request))
                    .isInstanceOf(AuthException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.EMAIL_ALREADY_EXISTS);
            verify(userRepository, never()).save(any());
        }
    }

    @Nested
    @DisplayName("login()")
    class Login {

        @Test
        @DisplayName("성공: 올바른 이메일+비밀번호로 로그인, 토큰 발급 및 실패 카운터 초기화")
        void success() {
            // given
            LoginRequest request = new LoginRequest("test@example.com", "Password1!");
            given(redisTemplate.hasKey("login:lock:test@example.com")).willReturn(false);
            given(userRepository.findByEmail("test@example.com")).willReturn(Optional.of(testUser));
            given(passwordEncoder.matches("Password1!", "encodedPassword")).willReturn(true);
            given(jwtUtil.generateAccessToken(1L, "USER")).willReturn("accessToken");
            given(jwtUtil.generateRefreshToken(1L)).willReturn("refreshToken");
            given(jwtUtil.getRefreshTokenExpiration()).willReturn(86400000L);

            // when
            TokenResponse response = authService.login(request);

            // then
            assertThat(response.accessToken()).isEqualTo("accessToken");
            assertThat(response.refreshToken()).isEqualTo("refreshToken");
            verify(redisTemplate).delete("login:fail:test@example.com");
            verify(valueOps).set(eq("refresh:1"), eq("refreshToken"), anyLong(), any());
        }

        @Test
        @DisplayName("실패: 계정 잠금 상태 → LOGIN_LOCKED")
        void fail_accountLocked() {
            // given
            LoginRequest request = new LoginRequest("test@example.com", "Password1!");
            given(redisTemplate.hasKey("login:lock:test@example.com")).willReturn(true);

            // when & then
            assertThatThrownBy(() -> authService.login(request))
                    .isInstanceOf(AuthException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.LOGIN_LOCKED);
            verify(userRepository, never()).findByEmail(any());
        }

        @Test
        @DisplayName("실패: 이메일 없음 → USER_INFO_MISMATCH + 실패 카운터 증가")
        void fail_emailNotFound() {
            // given
            LoginRequest request = new LoginRequest("notfound@example.com", "Password1!");
            given(redisTemplate.hasKey("login:lock:notfound@example.com")).willReturn(false);
            given(userRepository.findByEmail("notfound@example.com")).willReturn(Optional.empty());

            // when & then
            assertThatThrownBy(() -> authService.login(request))
                    .isInstanceOf(AuthException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.USER_INFO_MISMATCH);
            verify(redisTemplate).execute(any(), anyList(), any(), any());
        }

        @Test
        @DisplayName("실패: 비밀번호 불일치 → USER_INFO_MISMATCH + 실패 카운터 증가")
        void fail_passwordMismatch() {
            // given
            LoginRequest request = new LoginRequest("test@example.com", "WrongPassword1!");
            given(redisTemplate.hasKey("login:lock:test@example.com")).willReturn(false);
            given(userRepository.findByEmail("test@example.com")).willReturn(Optional.of(testUser));
            given(passwordEncoder.matches("WrongPassword1!", "encodedPassword")).willReturn(false);

            // when & then
            assertThatThrownBy(() -> authService.login(request))
                    .isInstanceOf(AuthException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.USER_INFO_MISMATCH);
            verify(redisTemplate).execute(any(), anyList(), any(), any());
        }
    }

    @Nested
    @DisplayName("reissue()")
    class Reissue {

        @Test
        @DisplayName("성공: 유효한 리프레시 토큰으로 새 토큰 발급")
        void success() {
            // given
            ReissueRequest request = new ReissueRequest("validRefreshToken");
            given(jwtUtil.validateToken("validRefreshToken")).willReturn(true);
            given(jwtUtil.getUserId("validRefreshToken")).willReturn(1L);
            given(valueOps.get("refresh:1")).willReturn("validRefreshToken");
            given(userRepository.findById(1L)).willReturn(Optional.of(testUser));
            given(jwtUtil.generateAccessToken(1L, "USER")).willReturn("newAccessToken");
            given(jwtUtil.generateRefreshToken(1L)).willReturn("newRefreshToken");
            given(jwtUtil.getRefreshTokenExpiration()).willReturn(86400000L);

            // when
            TokenResponse response = authService.reissue(request);

            // then
            assertThat(response.accessToken()).isEqualTo("newAccessToken");
            assertThat(response.refreshToken()).isEqualTo("newRefreshToken");
            verify(redisTemplate).delete("refresh:1");
        }

        @Test
        @DisplayName("실패: 유효하지 않은 토큰 → INVALID_REFRESH_TOKEN")
        void fail_invalidToken() {
            // given
            ReissueRequest request = new ReissueRequest("invalidToken");
            given(jwtUtil.validateToken("invalidToken")).willReturn(false);

            // when & then
            assertThatThrownBy(() -> authService.reissue(request))
                    .isInstanceOf(AuthException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_REFRESH_TOKEN);
            verify(userRepository, never()).findById(any());
        }

        @Test
        @DisplayName("실패: 토큰 재사용(stored != request) → INVALID_REFRESH_TOKEN + stored 토큰 삭제")
        void fail_tokenReplay() {
            // given
            ReissueRequest request = new ReissueRequest("replayToken");
            given(jwtUtil.validateToken("replayToken")).willReturn(true);
            given(jwtUtil.getUserId("replayToken")).willReturn(1L);
            given(valueOps.get("refresh:1")).willReturn("differentStoredToken");

            // when & then
            assertThatThrownBy(() -> authService.reissue(request))
                    .isInstanceOf(AuthException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_REFRESH_TOKEN);
            verify(redisTemplate).delete("refresh:1");
        }
    }

    @Nested
    @DisplayName("logout()")
    class Logout {

        @Test
        @DisplayName("성공: 유효한 토큰 → 블랙리스트 등록 + 리프레시 삭제")
        void success_validToken() {
            // given
            String accessToken = "validAccessToken";
            given(jwtUtil.validateToken(accessToken)).willReturn(true);
            given(jwtUtil.getExpiration(accessToken)).willReturn(30000L);
            given(jwtUtil.getUserIdIgnoringExpiration(accessToken)).willReturn(1L);

            // when
            authService.logout(accessToken);

            // then
            verify(valueOps).set(eq("blacklist:" + accessToken), eq("logout"), anyLong(), any());
            verify(redisTemplate).delete("refresh:1");
        }

        @Test
        @DisplayName("성공: 만료된 토큰 → 블랙리스트 미등록, 리프레시만 삭제")
        void success_expiredToken() {
            // given
            String expiredToken = "expiredAccessToken";
            given(jwtUtil.validateToken(expiredToken)).willReturn(false);
            given(jwtUtil.getUserIdIgnoringExpiration(expiredToken)).willReturn(1L);

            // when
            authService.logout(expiredToken);

            // then
            verify(valueOps, never()).set(contains("blacklist:"), anyString(), anyLong(), any());
            verify(redisTemplate).delete("refresh:1");
        }
    }
}
