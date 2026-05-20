package com.rocketcrew.pocat.domain.like.dto.request;

import jakarta.validation.constraints.NotNull;

public record ToggleLikeRequest(
        @NotNull(message = "경매 ID는 필수입니다.") Long auctionId
) {
}
