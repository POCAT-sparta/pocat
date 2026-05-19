package com.rocketcrew.pocat.domain.auction.dto.request;

import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record UpdateAuctionRequest(
        @Size(min = 1, max = 255, message = "경매 제목은 1자 이상 255자 이하여야 합니다.")
        String title,

        @Size(max = 65535, message = "경매 설명은 65535자 이하여야 합니다.")
        String description,

        @Positive(message = "시작가는 0보다 커야 합니다.")
        Long startingPrice,

        @Positive(message = "즉시구매가는 0보다 커야 합니다.")
        Long buyoutPrice
) {
}
