package com.rocketcrew.pocat.domain.chat.dto.response;

import com.rocketcrew.pocat.domain.chat.entity.Chat;
import com.rocketcrew.pocat.domain.chat.entity.enums.ChatStatus;

public record ChatResponse(
        Long chatId,
        Long postId,
        Long ownerId,
        Long guestId,
        ChatStatus status
) {
    public static ChatResponse from(Chat chat) {
        return new ChatResponse(
                chat.getId(),
                chat.getPostId(),
                chat.getOwnerId(),
                chat.getGuestId(),
                chat.getStatus()
        );
    }
}
