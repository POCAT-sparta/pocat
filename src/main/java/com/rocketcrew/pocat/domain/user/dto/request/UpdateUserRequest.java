package com.rocketcrew.pocat.domain.user.dto.request;

public record UpdateUserRequest(
        String nickname,
        String phone,
        String address
) {
}
