package com.rocketcrew.pocat.domain.chat.controller;

import com.rocketcrew.pocat.domain.chat.dto.request.CreateChatRequest;
import com.rocketcrew.pocat.domain.chat.dto.request.SendMessageRequest;
import com.rocketcrew.pocat.domain.chat.dto.response.ChatMessageResponse;
import com.rocketcrew.pocat.domain.chat.dto.response.ChatResponse;
import com.rocketcrew.pocat.domain.chat.service.ChatService;
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
@RequestMapping("/api/v1/chats")
public class ChatController {

    private final ChatService chatService;

    @PostMapping
    public ResponseEntity<ApiResponseDto<ChatResponse>> createChat(
            @RequestParam Long ownerId,
            @RequestBody CreateChatRequest request) {
        ChatResponse response = chatService.createChat(ownerId, request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponseDto.success(HttpStatus.CREATED, response));
    }

    @GetMapping
    public ResponseEntity<ApiResponseDto<PageResponseDto<ChatResponse>>> getMyChats(
            @RequestParam Long userId,
            @PageableDefault(size = 10) Pageable pageable) {
        Page<ChatResponse> page = chatService.getMyChats(userId, pageable);
        PageResponseDto<ChatResponse> response = PageResponseDto.of(page, page.getContent());
        return ResponseEntity.ok(ApiResponseDto.success(HttpStatus.OK, response));
    }

    @GetMapping("/{chatId}")
    public ResponseEntity<ApiResponseDto<ChatResponse>> getChat(@PathVariable Long chatId) {
        ChatResponse response = chatService.getChat(chatId);
        return ResponseEntity.ok(ApiResponseDto.success(HttpStatus.OK, response));
    }

    @GetMapping("/{chatId}/messages")
    public ResponseEntity<ApiResponseDto<PageResponseDto<ChatMessageResponse>>> getMessages(
            @PathVariable Long chatId,
            @PageableDefault(size = 20) Pageable pageable) {
        Page<ChatMessageResponse> page = chatService.getMessages(chatId, pageable);
        PageResponseDto<ChatMessageResponse> response = PageResponseDto.of(page, page.getContent());
        return ResponseEntity.ok(ApiResponseDto.success(HttpStatus.OK, response));
    }

    @PostMapping("/{chatId}/messages")
    public ResponseEntity<ApiResponseDto<ChatMessageResponse>> sendMessage(
            @PathVariable Long chatId,
            @RequestParam Long senderId,
            @RequestBody SendMessageRequest request) {
        ChatMessageResponse response = chatService.sendMessage(chatId, senderId, request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponseDto.success(HttpStatus.CREATED, response));
    }
}
