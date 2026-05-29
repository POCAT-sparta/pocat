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
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
public class AuthService {

    private static final int MAX_FAIL_COUNT = 5;
    private static final long LOCK_DURATION_SECONDS = 300; // 5분
    private static final String FAIL_KEY_PREFIX = "login:fail:";
    private static final String LOCK_KEY_PREFIX = "login:lock:";

    /**
     * Lua 스크립트: INCR + 첫 실패 TTL 설정 + 임계치 도달 시 잠금 전환을 원자적으로 처리.
     * 반환값: 1 = 잠금 전환됨, 0 = 아직 임계치 미달
     */
    private static final DefaultRedisScript<Long> LOGIN_FAIL_SCRIPT = new DefaultRedisScript<>("""
            local failKey  = KEYS[1]
            local lockKey  = KEYS[2]
            local max      = tonumber(ARGV[1])
            local ttl      = tonumber(ARGV[2])
            local count    = redis.call('INCR', failKey)
            if count == 1 then
                redis.call('EXPIRE', failKey, ttl)
            end
            if count >= max then
                redis.call('DEL', failKey)
                redis.call('SET', lockKey, 'locked', 'EX', ttl)
                return 1
            end
            return 0
            """, Long.class);

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

        try {
            userRepository.save(user);
        } catch (DataIntegrityViolationException e) {
            throw new AuthException(ErrorCode.EMAIL_ALREADY_EXISTS);
        }
        return new SignupResponse(user.getId(), user.getEmail(), user.getNickname());
    }

    @Transactional(readOnly = true)
    public TokenResponse login(LoginRequest request) {
        String email = request.email();

        // 잠금 확인 — 5회 실패 후 5분 잠금 (존재하지 않는 이메일도 동일하게 적용)
        if (Boolean.TRUE.equals(redisTemplate.hasKey(LOCK_KEY_PREFIX + email))) {
            throw new AuthException(ErrorCode.LOGIN_LOCKED);
        }

        User user = userRepository.findByEmail(email).orElse(null);

        // 이메일 미존재 또는 비밀번호 불일치 모두 실패 카운트에 포함
        if (user == null || !passwordEncoder.matches(request.password(), user.getPassword())) {
            handleLoginFailure(email);
            // 이메일 존재 여부를 노출하지 않도록 동일한 에러 반환
            throw new AuthException(ErrorCode.USER_INFO_MISMATCH);
        }

        // 로그인 성공 시 실패 카운터 초기화
        redisTemplate.delete(FAIL_KEY_PREFIX + email);
        return issueTokens(user);
    }

    /**
     * Lua 스크립트로 INCR + TTL + 잠금 전환을 원자적으로 처리한다.
     * - 존재하지 않는 이메일 시도도 동일하게 카운트
     * - 첫 실패 시 TTL 자동 설정으로 5분 슬라이딩 윈도우 보장
     * - MAX_FAIL_COUNT 도달 시 fail 키 삭제 후 lock 키 생성 (원자적)
     */
    private void handleLoginFailure(String email) {
        redisTemplate.execute(
                LOGIN_FAIL_SCRIPT,
                List.of(FAIL_KEY_PREFIX + email, LOCK_KEY_PREFIX + email),
                String.valueOf(MAX_FAIL_COUNT),
                String.valueOf(LOCK_DURATION_SECONDS)
        );
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
