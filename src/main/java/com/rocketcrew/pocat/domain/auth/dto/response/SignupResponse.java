package com.rocketcrew.pocat.domain.auth.dto.response;

public record SignupResponse(
        Long id,
        String email,
        String nickname
) {}
