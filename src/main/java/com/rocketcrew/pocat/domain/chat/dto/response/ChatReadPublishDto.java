package com.rocketcrew.pocat.domain.chat.dto.response;

public record ChatReadPublishDto(
        Long chatId,
        Long readerId
) {
}
