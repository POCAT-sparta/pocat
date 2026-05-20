package com.rocketcrew.pocat.domain.user.dto.request;

import jakarta.validation.constraints.NotBlank;

public record RegisterBillingKeyRequest(
        @NotBlank(message = "빌링키를 입력해 주세요.") String billingKey
) {}
