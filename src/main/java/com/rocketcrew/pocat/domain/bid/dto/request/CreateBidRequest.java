package com.rocketcrew.pocat.domain.bid.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record CreateBidRequest(
        @NotNull(message = "입찰가는 필수입니다.")
        @Positive(message = "입찰가는 0보다 커야 합니다.")
        Long bidPrice
) {
}
