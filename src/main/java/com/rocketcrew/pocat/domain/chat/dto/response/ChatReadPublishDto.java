package com.rocketcrew.pocat.domain.chat.dto.response;

public record ChatReadPublishDto(
        ChatEventType type,
        Long chatId,
        Long readerId
) {
}
