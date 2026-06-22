package com.rocketcrew.pocat.domain.auth.controller;

import io.swagger.v3.oas.annotations.tags.Tag;
import com.rocketcrew.pocat.domain.auth.dto.request.LoginRequest;
import com.rocketcrew.pocat.domain.auth.dto.request.ReissueRequest;
import com.rocketcrew.pocat.domain.auth.dto.request.SignupRequest;
import com.rocketcrew.pocat.domain.auth.dto.response.SignupResponse;
import com.rocketcrew.pocat.domain.auth.dto.response.TokenResponse;
import com.rocketcrew.pocat.domain.auth.service.AuthService;
import com.rocketcrew.pocat.global.dto.ApiResponseDto;
import com.rocketcrew.pocat.global.exception.common.ErrorCode;
import com.rocketcrew.pocat.global.exception.domain.AuthException;
import com.rocketcrew.pocat.global.ratelimit.RateLimitProperties;
import com.rocketcrew.pocat.global.ratelimit.RedisRateLimiter;
import com.rocketcrew.pocat.global.util.HttpRequestUtils;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

@Tag(name = "인증", description = "회원가입, 로그인, 로그아웃, 토큰 재발급")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api")
public class AuthController {

    private final AuthService authService;
    private final RedisRateLimiter redisRateLimiter;
    private final RateLimitProperties rateLimitProperties;

    @PostMapping("/v1/auth/signup")
    public ResponseEntity<ApiResponseDto<SignupResponse>> signup(
            @Valid @RequestBody SignupRequest request,
            HttpServletRequest httpRequest) {
        String ip = HttpRequestUtils.resolveClientIp(httpRequest);
        if (!redisRateLimiter.isAllowed("rate:ip:signup:" + ip,
                rateLimitProperties.getSignupLimit(),
                rateLimitProperties.getSignupWindowSeconds())) {
            throw new AuthException(ErrorCode.RATE_LIMIT_EXCEEDED);
        }
        SignupResponse response = authService.signup(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponseDto.success(HttpStatus.CREATED, response));
    }

    @PostMapping("/v1/auth/login")
    public ResponseEntity<ApiResponseDto<TokenResponse>> login(
            @Valid @RequestBody LoginRequest request,
            HttpServletRequest httpRequest) {
        String ip = HttpRequestUtils.resolveClientIp(httpRequest);
        if (!redisRateLimiter.isAllowed("rate:ip:login:" + ip,
                rateLimitProperties.getLoginLimit(),
                rateLimitProperties.getLoginWindowSeconds())) {
            throw new AuthException(ErrorCode.RATE_LIMIT_EXCEEDED);
        }
        TokenResponse response = authService.login(request);
        return ResponseEntity.ok(ApiResponseDto.success(HttpStatus.OK, response));
    }

    @PostMapping("/v1/auth/reissue")
    public ResponseEntity<ApiResponseDto<TokenResponse>> reissue(
            @Valid @RequestBody ReissueRequest request,
            HttpServletRequest httpRequest) {
        String ip = HttpRequestUtils.resolveClientIp(httpRequest);
        if (!redisRateLimiter.isAllowed("rate:ip:reissue:" + ip,
                rateLimitProperties.getReissueLimit(),
                rateLimitProperties.getReissueWindowSeconds())) {
            throw new AuthException(ErrorCode.RATE_LIMIT_EXCEEDED);
        }
        TokenResponse response = authService.reissue(request);
        return ResponseEntity.ok(ApiResponseDto.success(HttpStatus.OK, response));
    }

    @PostMapping("/v1/auth/logout")
    public ResponseEntity<ApiResponseDto<Void>> logout(
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        if (!StringUtils.hasText(authorization) || !authorization.startsWith("Bearer ")) {
            throw new AuthException(ErrorCode.INVALID_ACCESS_TOKEN);
        }
        String token = authorization.substring(7);
        if (!StringUtils.hasText(token)) {
            throw new AuthException(ErrorCode.INVALID_ACCESS_TOKEN);
        }
        authService.logout(token);
        return ResponseEntity.ok(ApiResponseDto.success(HttpStatus.OK, null));
    }
}
