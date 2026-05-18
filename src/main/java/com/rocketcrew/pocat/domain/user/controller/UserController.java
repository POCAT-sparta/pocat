package com.rocketcrew.pocat.domain.user.controller;

import com.rocketcrew.pocat.domain.user.dto.request.UpdateBankRequest;
import com.rocketcrew.pocat.domain.user.dto.request.UpdateUserRequest;
import com.rocketcrew.pocat.domain.user.dto.response.UserResponse;
import com.rocketcrew.pocat.domain.user.service.UserCommandService;
import com.rocketcrew.pocat.domain.user.service.UserQueryService;
import com.rocketcrew.pocat.global.dto.ApiResponseDto;
import com.rocketcrew.pocat.global.dto.PageResponseDto;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/users")
public class UserController {

    private final UserQueryService userQueryService;
    private final UserCommandService userCommandService;

    @GetMapping("/{userId}")
    public ResponseEntity<ApiResponseDto<UserResponse>> getUserById(@PathVariable Long userId) {
        UserResponse response = userQueryService.getUserById(userId);
        return ResponseEntity.ok(ApiResponseDto.success(HttpStatus.OK, response));
    }

    @PutMapping("/{userId}")
    public ResponseEntity<ApiResponseDto<UserResponse>> updateUser(
            @PathVariable Long userId,
            @RequestBody UpdateUserRequest request) {
        UserResponse response = userCommandService.updateUser(userId, request);
        return ResponseEntity.ok(ApiResponseDto.success(HttpStatus.OK, response));
    }

    @PutMapping("/{userId}/bank")
    public ResponseEntity<ApiResponseDto<UserResponse>> updateBank(
            @PathVariable Long userId,
            @RequestBody UpdateBankRequest request) {
        UserResponse response = userCommandService.updateBank(userId, request);
        return ResponseEntity.ok(ApiResponseDto.success(HttpStatus.OK, response));
    }

    @GetMapping
    public ResponseEntity<ApiResponseDto<PageResponseDto<UserResponse>>> getAllUsers(
            @PageableDefault(size = 10, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        Page<UserResponse> page = userQueryService.getAllUsers(pageable);
        List<UserResponse> content = page.getContent();
        PageResponseDto<UserResponse> pageResponse = PageResponseDto.of(page, content);
        return ResponseEntity.ok(ApiResponseDto.success(HttpStatus.OK, pageResponse));
    }
}
