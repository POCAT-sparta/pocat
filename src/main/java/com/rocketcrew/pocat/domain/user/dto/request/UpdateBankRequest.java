package com.rocketcrew.pocat.domain.user.dto.request;

public record UpdateBankRequest(
        String bankName,
        String bankAccount
) {
}
