package com.rocketcrew.pocat.domain.notification.controller;

import com.rocketcrew.pocat.domain.notification.dto.response.NotificationResponse;
import com.rocketcrew.pocat.domain.notification.service.NotificationService;
import com.rocketcrew.pocat.global.dto.ApiResponseDto;
import com.rocketcrew.pocat.global.dto.PageResponseDto;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api")
public class NotificationController {

    private final NotificationService notificationService;

    @GetMapping("/v1/notifications")
    public ResponseEntity<ApiResponseDto<PageResponseDto<NotificationResponse>>> getNotifications(
            @RequestParam Long userId,
            @PageableDefault(size = 10) Pageable pageable) {
        Page<NotificationResponse> page = notificationService.getNotifications(userId, pageable);
        PageResponseDto<NotificationResponse> response = PageResponseDto.of(page, page.getContent());
        return ResponseEntity.ok(ApiResponseDto.success(HttpStatus.OK, response));
    }

    @PutMapping("/v1/notifications/{notificationId}/read")
    public ResponseEntity<ApiResponseDto<NotificationResponse>> markAsRead(@PathVariable Long notificationId) {
        NotificationResponse response = notificationService.markAsRead(notificationId);
        return ResponseEntity.ok(ApiResponseDto.success(HttpStatus.OK, response));
    }
}
