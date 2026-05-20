package com.rocketcrew.pocat.domain.chat.dto.response;

import com.rocketcrew.pocat.domain.chat.entity.enums.ChatStatus;

import java.time.LocalDateTime;

public record ChatRoomListResponse(
        Long chatId,
        String postTitle,
        String opponentNickname,
        String lastMessage,
        ChatStatus status,
        LocalDateTime updatedAt
) {
}
