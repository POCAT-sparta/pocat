package com.rocketcrew.pocat.domain.chat.dto.request;

public record CreateChatRequest(
        Long guestId,
        Long postId
) {
}
