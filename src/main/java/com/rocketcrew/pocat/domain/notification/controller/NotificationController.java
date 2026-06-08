package com.rocketcrew.pocat.domain.notification.controller;

import com.rocketcrew.pocat.domain.notification.dto.response.NotificationListResponse;
import com.rocketcrew.pocat.domain.notification.dto.response.NotificationResponse;
import com.rocketcrew.pocat.domain.notification.service.NotificationCommandService;
import com.rocketcrew.pocat.domain.notification.service.NotificationQueryService;
import com.rocketcrew.pocat.global.dto.ApiResponseDto;
import com.rocketcrew.pocat.global.security.CustomUserDetails;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api")
public class NotificationController {

    private final NotificationQueryService notificationQueryService;
    private final NotificationCommandService notificationCommandService;

    @GetMapping("/v1/notifications")
    public ResponseEntity<ApiResponseDto<NotificationListResponse>> getNotifications(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestParam(required = false) Long cursor
    ) {
        NotificationListResponse response =
                notificationQueryService.getNotifications(userDetails.getUserId(), cursor);
        return ResponseEntity.ok(ApiResponseDto.success(HttpStatus.OK, response));
    }

    @PutMapping("/v1/notifications/{notificationId}/read")
    public ResponseEntity<ApiResponseDto<NotificationResponse>> read(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long notificationId
    ) {
        NotificationResponse response =
                notificationCommandService.read(userDetails.getUserId(), notificationId);
        return ResponseEntity.ok(ApiResponseDto.success(HttpStatus.OK, response));
    }

    @PutMapping("/v1/notifications/read")
    public ResponseEntity<ApiResponseDto<Void>> readAll(
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        notificationCommandService.readAll(userDetails.getUserId());
        return ResponseEntity.ok(ApiResponseDto.successWithNoContent());
    }

    @DeleteMapping("/v1/notifications/{notificationId}")
    public ResponseEntity<ApiResponseDto<Void>> delete(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long notificationId
    ) {
        notificationCommandService.delete(userDetails.getUserId(), notificationId);
        return ResponseEntity.ok(ApiResponseDto.successWithNoContent());
    }

    @DeleteMapping("/v1/notifications")
    public ResponseEntity<ApiResponseDto<Void>> deleteAll(
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        notificationCommandService.deleteAll(userDetails.getUserId());
        return ResponseEntity.ok(ApiResponseDto.successWithNoContent());
    }
}
