package com.rocketcrew.pocat.domain.auction.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record CreateAuctionRequest(
        @NotNull(message = "카드 ID는 필수입니다.")
        @Positive(message = "카드 ID는 0보다 커야 합니다.")
        Long cardId,

        @NotBlank(message = "경매 제목은 필수입니다.")
        @Size(max = 255, message = "경매 제목은 255자 이하여야 합니다.")
        String title,

        @Size(max = 65535, message = "경매 설명은 65535자 이하여야 합니다.")
        String description,

        @NotNull(message = "시작가는 필수입니다.")
        @Positive(message = "시작가는 0보다 커야 합니다.")
        Long startingPrice,

        @Positive(message = "즉시구매가는 0보다 커야 합니다.")
        Long buyoutPrice
) {
}
