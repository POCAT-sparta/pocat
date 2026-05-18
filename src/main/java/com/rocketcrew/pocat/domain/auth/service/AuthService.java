package com.rocketcrew.pocat.domain.auth.service;

import com.rocketcrew.pocat.domain.auth.dto.request.LoginRequest;
import com.rocketcrew.pocat.domain.auth.dto.request.ReissueRequest;
import com.rocketcrew.pocat.domain.auth.dto.request.SignupRequest;
import com.rocketcrew.pocat.domain.auth.dto.response.SignupResponse;
import com.rocketcrew.pocat.domain.auth.dto.response.TokenResponse;
import com.rocketcrew.pocat.domain.user.entity.Role;
import com.rocketcrew.pocat.domain.user.entity.User;
import com.rocketcrew.pocat.domain.user.repository.UserRepository;
import com.rocketcrew.pocat.global.exception.common.ErrorCode;
import com.rocketcrew.pocat.global.exception.common.ServiceException;
import com.rocketcrew.pocat.global.jwt.JwtUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final StringRedisTemplate redisTemplate;

    @Transactional
    public SignupResponse signup(SignupRequest request) {
        if (userRepository.existsByEmail(request.email())) {
            throw new ServiceException(ErrorCode.EMAIL_ALREADY_EXISTS);
        }

        User user = User.builder()
                .email(request.email())
                .password(passwordEncoder.encode(request.password()))
                .nickname(request.nickname())
                .phone(request.phone())
                .role(Role.USER)
                .build();

        userRepository.save(user);
        return new SignupResponse(user.getId(), user.getEmail(), user.getNickname());
    }

    @Transactional(readOnly = true)
    public TokenResponse login(LoginRequest request) {
        User user = userRepository.findByEmail(request.email())
                .orElseThrow(() -> new ServiceException(ErrorCode.USER_NOT_FOUND));

        if (!passwordEncoder.matches(request.password(), user.getPassword())) {
            throw new ServiceException(ErrorCode.USER_INFO_MISMATCH);
        }

        return issueTokens(user);
    }

    @Transactional
    public TokenResponse reissue(ReissueRequest request) {
        String refreshToken = request.refreshToken();

        if (!jwtUtil.validateToken(refreshToken)) {
            throw new ServiceException(ErrorCode.INVALID_REFRESH_TOKEN);
        }

        Long userId = jwtUtil.getUserId(refreshToken);
        String storedToken = redisTemplate.opsForValue().get("refresh:" + userId);

        if (!refreshToken.equals(storedToken)) {
            // 이미 사용된 토큰 → 탈취 가능성으로 판단하여 저장된 토큰도 삭제
            redisTemplate.delete("refresh:" + userId);
            throw new ServiceException(ErrorCode.INVALID_REFRESH_TOKEN);
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ServiceException(ErrorCode.USER_NOT_FOUND));

        redisTemplate.delete("refresh:" + userId);
        return issueTokens(user);
    }

    public void logout(String accessToken) {
        long expiration = jwtUtil.getExpiration(accessToken);
        if (expiration > 0) {
            redisTemplate.opsForValue()
                    .set("blacklist:" + accessToken, "logout", expiration, TimeUnit.MILLISECONDS);
        }

        Long userId = jwtUtil.getUserId(accessToken);
        redisTemplate.delete("refresh:" + userId);
    }

    private TokenResponse issueTokens(User user) {
        String accessToken = jwtUtil.generateAccessToken(user.getId(), user.getRole().name());
        String refreshToken = jwtUtil.generateRefreshToken(user.getId());

        long refreshExpiration = jwtUtil.getRefreshTokenExpiration();
        redisTemplate.opsForValue()
                .set("refresh:" + user.getId(), refreshToken, refreshExpiration, TimeUnit.MILLISECONDS);

        return new TokenResponse(accessToken, refreshToken);
    }
}
