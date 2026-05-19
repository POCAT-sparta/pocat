package com.rocketcrew.pocat.domain.user.controller;

import com.rocketcrew.pocat.domain.user.dto.request.RegisterBillingKeyRequest;
import com.rocketcrew.pocat.domain.user.dto.request.UpdateBankRequest;
import com.rocketcrew.pocat.domain.user.dto.request.UpdateUserRequest;
import com.rocketcrew.pocat.domain.user.dto.response.UserResponse;
import com.rocketcrew.pocat.domain.user.service.UserCommandService;
import com.rocketcrew.pocat.domain.user.service.UserQueryService;
import com.rocketcrew.pocat.global.dto.ApiResponseDto;
import com.rocketcrew.pocat.global.dto.PageResponseDto;
import com.rocketcrew.pocat.global.security.CustomUserDetails;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
public class UserController {

    private final UserQueryService userQueryService;
    private final UserCommandService userCommandService;

    @GetMapping("/api/v1/users/me")
    public ResponseEntity<ApiResponseDto<UserResponse>> getUserMe(@AuthenticationPrincipal CustomUserDetails userDetails) {
        UserResponse response = userQueryService.getUserById(userDetails.getUserId());
        return ResponseEntity.ok(ApiResponseDto.success(HttpStatus.OK, response));
    }

    @PatchMapping("/api/v1/users/me")
    public ResponseEntity<ApiResponseDto<UserResponse>> updateUser(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestBody UpdateUserRequest request) {
        UserResponse response = userCommandService.updateUser(userDetails.getUserId(), request);
        return ResponseEntity.ok(ApiResponseDto.success(HttpStatus.OK, response));
    }

    @PostMapping("/api/v1/users/me/billing-key")
    public ResponseEntity<ApiResponseDto<Void>> registerBillingKey(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestBody RegisterBillingKeyRequest request) {
        userCommandService.registerBillingKey(userDetails.getUserId(), request);
        return ResponseEntity.ok(ApiResponseDto.success(HttpStatus.OK, null));
    }

    @DeleteMapping("/api/v1/users/me/billing-key")
    public ResponseEntity<ApiResponseDto<Void>> deleteBillingKey(@AuthenticationPrincipal CustomUserDetails userDetails) {
        userCommandService.deleteBillingKey(userDetails.getUserId());
        return ResponseEntity.ok(ApiResponseDto.success(HttpStatus.OK, null));
    }

    @PutMapping("/api/v1/users/me/bank-account")
    public ResponseEntity<ApiResponseDto<Void>> updateBank(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestBody UpdateBankRequest request) {
        userCommandService.updateBank(userDetails.getUserId(), request);
        return ResponseEntity.ok(ApiResponseDto.success(HttpStatus.OK, null));
    }

    @GetMapping("/api/v1/admin/users")
    public ResponseEntity<ApiResponseDto<PageResponseDto<UserResponse>>> getAllUsers(
            @PageableDefault(size = 10, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        Page<UserResponse> page = userQueryService.getAllUsers(pageable);
        PageResponseDto<UserResponse> pageResponse = PageResponseDto.of(page, page.getContent());
        return ResponseEntity.ok(ApiResponseDto.success(HttpStatus.OK, pageResponse));
    }
}
