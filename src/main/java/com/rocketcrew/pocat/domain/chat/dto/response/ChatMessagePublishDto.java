package com.rocketcrew.pocat.domain.chat.dto.response;

import java.time.LocalDateTime;

public record ChatMessagePublishDto(
        Long chatId,
        Long senderId,
        String senderNickname,
        String message,
        LocalDateTime createdAt
) {
}
