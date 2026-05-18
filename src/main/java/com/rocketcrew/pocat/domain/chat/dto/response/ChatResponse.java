package com.rocketcrew.pocat.domain.chat.dto.response;

import com.rocketcrew.pocat.domain.chat.entity.Chat;
import com.rocketcrew.pocat.domain.chat.entity.enums.ChatStatus;

import java.time.LocalDateTime;

public record ChatResponse(
        Long id,
        Long ownerId,
        Long guestId,
        Long postId,
        ChatStatus status,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public static ChatResponse from(Chat chat) {
        return new ChatResponse(
                chat.getId(),
                chat.getOwnerId(),
                chat.getGuestId(),
                chat.getPostId(),
                chat.getStatus(),
                chat.getCreatedAt(),
                chat.getUpdatedAt()
        );
    }
}
