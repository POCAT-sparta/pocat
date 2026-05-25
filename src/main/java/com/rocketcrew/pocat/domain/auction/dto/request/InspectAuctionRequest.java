package com.rocketcrew.pocat.domain.auction.dto.request;

import com.rocketcrew.pocat.domain.auction.enums.AuctionInspectionResult;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record InspectAuctionRequest(
        @NotNull(message = "검수 결과는 필수입니다.")
        AuctionInspectionResult result,

        @Size(max = 65535, message = "사유는 65535자 이하여야 합니다.")
        String reason
) {
}
