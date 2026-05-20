package com.rocketcrew.pocat.domain.user.dto.request;

import jakarta.validation.constraints.NotBlank;

public record UpdateBankRequest(
        @NotBlank(message = "은행명을 입력해 주세요.") String bankName,
        @NotBlank(message = "계좌번호를 입력해 주세요.") String bankAccount
) {
}
