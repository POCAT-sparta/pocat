package com.rocketcrew.pocat.domain.community.tradepost.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

public record CreateTradePostRequest(
        @NotBlank
        @Size(max = 100, message = "제목은 100자를 초과할 수 없습니다")
        String title,

        @NotBlank
        @Size(max = 5000, message = "내용은 5000자를 초과할 수 없습니다")
        String content,

        @NotNull
        @PositiveOrZero
        Long price,

        @NotBlank
        @Size(max = 500, message = "썸네일 URL은 500자를 초과할 수 없습니다")
        String thumbnail
) {
}
