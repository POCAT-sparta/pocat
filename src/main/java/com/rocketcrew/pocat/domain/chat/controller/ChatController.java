package com.rocketcrew.pocat.domain.chat.controller;

import com.rocketcrew.pocat.domain.chat.dto.request.CreateChatRequest;
import com.rocketcrew.pocat.domain.chat.dto.response.ChatMessageResponse;
import com.rocketcrew.pocat.domain.chat.dto.response.ChatResponse;
import com.rocketcrew.pocat.domain.chat.dto.response.ChatRoomListResponse;
import com.rocketcrew.pocat.domain.chat.service.ChatCommandService;
import com.rocketcrew.pocat.domain.chat.service.ChatQueryService;
import com.rocketcrew.pocat.global.dto.ApiResponseDto;
import com.rocketcrew.pocat.global.dto.PageResponseDto;
import com.rocketcrew.pocat.global.security.CustomUserDetails;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/chats")
public class ChatController {

    private final ChatCommandService chatCommandService;
    private final ChatQueryService chatQueryService;

    @PostMapping
    public ResponseEntity<ApiResponseDto<ChatResponse>> createChat(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @Valid @RequestBody CreateChatRequest request) {
        ChatResponse response = chatCommandService.createChat(userDetails.getUserId(), request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponseDto.success(HttpStatus.CREATED, response));
    }

    @GetMapping("/me")
    public ResponseEntity<ApiResponseDto<List<ChatRoomListResponse>>> getMyChats(
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        List<ChatRoomListResponse> response = chatQueryService.getMyChats(userDetails.getUserId());
        return ResponseEntity.ok(ApiResponseDto.success(HttpStatus.OK, response));
    }

    @GetMapping("/{chatId}/messages")
    public ResponseEntity<ApiResponseDto<PageResponseDto<ChatMessageResponse>>> getMessages(
            @PathVariable Long chatId,
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PageableDefault(size = 30) Pageable pageable) {
        Page<ChatMessageResponse> page = chatQueryService.getMessages(chatId, userDetails.getUserId(), pageable);
        return ResponseEntity.ok(ApiResponseDto.success(HttpStatus.OK, PageResponseDto.of(page, page.getContent())));
    }

    @PatchMapping("/{chatId}/read")
    public ResponseEntity<ApiResponseDto<Void>> markAsRead(
            @PathVariable Long chatId,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        chatCommandService.markAsRead(chatId, userDetails.getUserId());
        return ResponseEntity.ok(ApiResponseDto.successWithNoContent());
    }

    @DeleteMapping("/{chatId}")
    public ResponseEntity<ApiResponseDto<Void>> leaveChat(
            @PathVariable Long chatId,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        chatCommandService.leaveChat(chatId, userDetails.getUserId());
        return ResponseEntity.ok(ApiResponseDto.successWithNoContent());
    }
}
