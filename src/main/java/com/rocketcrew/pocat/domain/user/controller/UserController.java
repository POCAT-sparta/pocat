package com.rocketcrew.pocat.domain.user.controller;

import io.swagger.v3.oas.annotations.tags.Tag;
import com.rocketcrew.pocat.domain.user.dto.request.RegisterBillingKeyRequest;
import com.rocketcrew.pocat.domain.user.dto.request.UpdateBillingKeyRequest;
import com.rocketcrew.pocat.domain.user.dto.request.UpdateUserRequest;
import com.rocketcrew.pocat.domain.user.dto.response.AdminUserResponse;
import com.rocketcrew.pocat.domain.user.dto.response.UserResponse;
import com.rocketcrew.pocat.domain.user.service.UserCommandService;
import com.rocketcrew.pocat.domain.user.service.UserQueryService;
import com.rocketcrew.pocat.global.dto.ApiResponseDto;
import com.rocketcrew.pocat.global.dto.PageResponseDto;
import com.rocketcrew.pocat.global.exception.common.ErrorCode;
import com.rocketcrew.pocat.global.exception.common.ServiceException;
import com.rocketcrew.pocat.global.ratelimit.RateLimitProperties;
import com.rocketcrew.pocat.global.ratelimit.RedisRateLimiter;
import com.rocketcrew.pocat.global.security.CustomUserDetails;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@Tag(name = "사용자", description = "유저 프로필 및 결제 수단 관리")
@RestController
@RequiredArgsConstructor
public class UserController {

    private final UserQueryService userQueryService;
    private final UserCommandService userCommandService;
    private final RedisRateLimiter redisRateLimiter;
    private final RateLimitProperties rateLimitProperties;

    @GetMapping("/api/v1/users/me")
    public ResponseEntity<ApiResponseDto<UserResponse>> getUserMe(@AuthenticationPrincipal CustomUserDetails userDetails) {
        UserResponse response = userQueryService.getUserById(userDetails.getUserId());
        return ResponseEntity.ok(ApiResponseDto.success(HttpStatus.OK, response));
    }

    @PatchMapping("/api/v1/users/me")
    public ResponseEntity<ApiResponseDto<UserResponse>> updateUser(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @Valid @RequestBody UpdateUserRequest request) {
        if (!redisRateLimiter.isAllowed("rate:user:user:" + userDetails.getUserId(),
                rateLimitProperties.getUserLimit(),
                rateLimitProperties.getUserWindowSeconds())) {
            throw new ServiceException(ErrorCode.RATE_LIMIT_EXCEEDED);
        }
        UserResponse response = userCommandService.updateUser(userDetails.getUserId(), request);
        return ResponseEntity.ok(ApiResponseDto.success(HttpStatus.OK, response));
    }

    @PostMapping("/api/v1/users/me/billing-key")
    public ResponseEntity<ApiResponseDto<Void>> registerBillingKey(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @Valid @RequestBody RegisterBillingKeyRequest request) {
        if (!redisRateLimiter.isAllowed("rate:user:user:" + userDetails.getUserId(),
                rateLimitProperties.getUserLimit(),
                rateLimitProperties.getUserWindowSeconds())) {
            throw new ServiceException(ErrorCode.RATE_LIMIT_EXCEEDED);
        }
        userCommandService.registerBillingKey(userDetails.getUserId(), request);
        return ResponseEntity.ok(ApiResponseDto.success(HttpStatus.OK, null));
    }

    @DeleteMapping("/api/v1/users/me/billing-key")
    public ResponseEntity<ApiResponseDto<Void>> deleteBillingKey(@AuthenticationPrincipal CustomUserDetails userDetails) {
        if (!redisRateLimiter.isAllowed("rate:user:user:" + userDetails.getUserId(),
                rateLimitProperties.getUserLimit(),
                rateLimitProperties.getUserWindowSeconds())) {
            throw new ServiceException(ErrorCode.RATE_LIMIT_EXCEEDED);
        }
        userCommandService.deleteBillingKey(userDetails.getUserId());
        return ResponseEntity.ok(ApiResponseDto.success(HttpStatus.OK, null));
    }

    @PutMapping("/api/v1/users/me/billing-key")
    public ResponseEntity<ApiResponseDto<Void>> updateBillingKey(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @Valid @RequestBody UpdateBillingKeyRequest request) {
        if (!redisRateLimiter.isAllowed("rate:user:user:" + userDetails.getUserId(),
                rateLimitProperties.getUserLimit(),
                rateLimitProperties.getUserWindowSeconds())) {
            throw new ServiceException(ErrorCode.RATE_LIMIT_EXCEEDED);
        }
        userCommandService.updateBillingKey(userDetails.getUserId(), request);
        return ResponseEntity.ok(ApiResponseDto.success(HttpStatus.OK, null));
    }

    @GetMapping("/api/v1/admin/users")
    public ResponseEntity<ApiResponseDto<PageResponseDto<AdminUserResponse>>> getAllUsers(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Boolean isBidBlocked,
            @PageableDefault(size = 10, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        Page<AdminUserResponse> page = userQueryService.getAllUsers(keyword, isBidBlocked, pageable);
        PageResponseDto<AdminUserResponse> pageResponse = PageResponseDto.of(page, page.getContent());
        return ResponseEntity.ok(ApiResponseDto.success(HttpStatus.OK, pageResponse));
    }
}
