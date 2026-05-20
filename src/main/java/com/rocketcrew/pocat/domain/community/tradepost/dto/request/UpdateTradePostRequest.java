package com.rocketcrew.pocat.domain.community.tradepost.dto.request;

import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

public record UpdateTradePostRequest(

        @Size(max = 100, message = "제목은 100자를 초과할 수 없습니다")
        String title,

        @Size(max = 5000, message = "내용은 5000자를 초과할 수 없습니다")
        String content,

        @PositiveOrZero
        Long price,

        @Size(max = 500, message = "썸네일 URL은 500자를 초과할 수 없습니다")
        String thumbnail
) {
}
