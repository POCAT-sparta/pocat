package com.rocketcrew.pocat.global.dto;

import lombok.Builder;

@Builder
public record LoginResponseDto(
        String refreshToken,
        String email
) {}