package com.rocketcrew.pocat.domain.community.tradepost.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CreateTradePostRequest(
        @NotBlank
        String title,

        @NotBlank
        String content,

        @NotNull
        Long price,

        @NotBlank
        String thumbnail
) {
}
