package com.rocketcrew.pocat.domain.chat.dto.response;

import com.rocketcrew.pocat.domain.chat.entity.ChatMessage;

import java.time.LocalDateTime;

public record ChatMessageResponse(
        Long id,
        Long chatId,
        Long senderId,
        String message,
        boolean isRead,
        LocalDateTime createdAt
) {
    public static ChatMessageResponse from(ChatMessage chatMessage) {
        return new ChatMessageResponse(
                chatMessage.getId(),
                chatMessage.getChatId(),
                chatMessage.getSenderId(),
                chatMessage.getMessage(),
                chatMessage.isRead(),
                chatMessage.getCreatedAt()
        );
    }
}
