package com.rocketcrew.pocat.domain.auth.dto.response;

public record TokenResponse(
        String accessToken,
        String refreshToken
) {}
