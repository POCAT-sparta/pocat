package com.rocketcrew.pocat.domain.auction.dto.request;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record UpdateAuctionRequest(
        @Size(max = 255, message = "경매 제목은 255자 이하여야 합니다.")
        @Pattern(regexp = ".*\\S.*", message = "경매 제목은 공백만 입력할 수 없습니다.")
        String title,

        @Size(max = 65535, message = "경매 설명은 65535자 이하여야 합니다.")
        @Pattern(regexp = ".*\\S.*", message = "경매 설명은 공백만 입력할 수 없습니다.")
        String description,

        @Positive(message = "시작가는 0보다 커야 합니다.")
        Long startingPrice,

        @Positive(message = "즉시구매가는 0보다 커야 합니다.")
        Long buyoutPrice
) {
}