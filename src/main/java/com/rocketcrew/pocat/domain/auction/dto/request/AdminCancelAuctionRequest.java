package com.rocketcrew.pocat.domain.auction.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AdminCancelAuctionRequest(
        @NotBlank(message = "취소 사유를 입력해주세요.")
        @Size(max = 65535, message = "취소 사유는 65535자 이하여야 합니다.")
        String reason
) {
}
