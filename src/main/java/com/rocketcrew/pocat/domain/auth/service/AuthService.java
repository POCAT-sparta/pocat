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
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
public class AuthService {

    private static final int MAX_FAIL_COUNT = 5;
    private static final long LOCK_DURATION_MINUTES = 5;
    private static final String FAIL_KEY_PREFIX = "login:fail:";
    private static final String LOCK_KEY_PREFIX = "login:lock:";

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final StringRedisTemplate redisTemplate;

    @Transactional
    public SignupResponse signup(SignupRequest request) {
        if (userRepository.existsByEmail(request.email())) {
            throw new AuthException(ErrorCode.EMAIL_ALREADY_EXISTS);
        }

        User user = User.builder()
                .email(request.email())
                .password(passwordEncoder.encode(request.password()))
                .nickname(request.nickname())
                .phone(request.phone())
                .userRole(UserRole.USER)
                .build();

        userRepository.save(user);
        return new SignupResponse(user.getId(), user.getEmail(), user.getNickname());
    }

    @Transactional(readOnly = true)
    public TokenResponse login(LoginRequest request) {
        String email = request.email();

        // 잠금 확인 — 5회 실패 후 5분 잠금
        if (Boolean.TRUE.equals(redisTemplate.hasKey(LOCK_KEY_PREFIX + email))) {
            throw new AuthException(ErrorCode.LOGIN_LOCKED);
        }

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new AuthException(ErrorCode.USER_NOT_FOUND));

        if (!passwordEncoder.matches(request.password(), user.getPassword())) {
            handleLoginFailure(email);
            throw new AuthException(ErrorCode.USER_INFO_MISMATCH);
        }

        // 로그인 성공 시 실패 카운터 초기화
        redisTemplate.delete(FAIL_KEY_PREFIX + email);
        return issueTokens(user);
    }

    /**
     * 로그인 실패 횟수를 Redis에 누적하고 MAX_FAIL_COUNT 도달 시 계정을 잠근다.
     * - 첫 실패 시 TTL(5분)을 설정해 5분 단위로 카운터가 리셋된다.
     * - MAX_FAIL_COUNT 도달 시 fail 키를 삭제하고 lock 키를 별도 저장한다.
     */
    private void handleLoginFailure(String email) {
        String failKey = FAIL_KEY_PREFIX + email;
        Long count = redisTemplate.opsForValue().increment(failKey);

        if (count != null && count == 1) {
            // 첫 실패: TTL 시작 (5분 내 5회 초과 시 잠금)
            redisTemplate.expire(failKey, LOCK_DURATION_MINUTES, TimeUnit.MINUTES);
        }

        if (count != null && count >= MAX_FAIL_COUNT) {
            redisTemplate.delete(failKey);
            redisTemplate.opsForValue()
                    .set(LOCK_KEY_PREFIX + email, "locked", LOCK_DURATION_MINUTES, TimeUnit.MINUTES);
        }
    }

    public TokenResponse reissue(ReissueRequest request) {
        String refreshToken = request.refreshToken();

        if (!jwtUtil.validateToken(refreshToken)) {
            throw new AuthException(ErrorCode.INVALID_REFRESH_TOKEN);
        }

        Long userId = jwtUtil.getUserId(refreshToken);
        String storedToken = redisTemplate.opsForValue().get("refresh:" + userId);

        if (!refreshToken.equals(storedToken)) {
            // 이미 사용된 토큰 → 탈취 가능성으로 판단하여 저장된 토큰도 삭제
            redisTemplate.delete("refresh:" + userId);
            throw new AuthException(ErrorCode.INVALID_REFRESH_TOKEN);
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new AuthException(ErrorCode.USER_NOT_FOUND));

        redisTemplate.delete("refresh:" + userId);
        return issueTokens(user);
    }

    public void logout(String accessToken) {
        // 아직 유효한 토큰만 블랙리스트 등록 — 만료된 토큰은 이미 무효이므로 등록 불필요
        if (jwtUtil.validateToken(accessToken)) {
            long expiration = jwtUtil.getExpiration(accessToken);
            if (expiration > 0) {
                redisTemplate.opsForValue()
                        .set("blacklist:" + accessToken, "logout", expiration, TimeUnit.MILLISECONDS);
            }
        }

        // 만료 여부와 관계없이 refresh 토큰은 항상 삭제
        Long userId = jwtUtil.getUserIdIgnoringExpiration(accessToken);
        if (userId != null) {
            redisTemplate.delete("refresh:" + userId);
        }
    }

    private TokenResponse issueTokens(User user) {
        String accessToken = jwtUtil.generateAccessToken(user.getId(), user.getUserRole().name());
        String refreshToken = jwtUtil.generateRefreshToken(user.getId());

        long refreshExpiration = jwtUtil.getRefreshTokenExpiration();
        redisTemplate.opsForValue()
                .set("refresh:" + user.getId(), refreshToken, refreshExpiration, TimeUnit.MILLISECONDS);

        return new TokenResponse(accessToken, refreshToken);
    }
}
