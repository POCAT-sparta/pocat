package com.rocketcrew.pocat.domain.user.dto.response;

import com.rocketcrew.pocat.domain.user.entity.Role;
import com.rocketcrew.pocat.domain.user.entity.User;

import java.time.LocalDateTime;

public record UserResponse(
        Long id,
        String email,
        String nickname,
        String phone,
        Role role,
        String bankName,
        String bankAccount,
        String address,
        int unpaidStrike,
        boolean isBidBlocked,
        LocalDateTime createdAt
) {
    public static UserResponse from(User user) {
        return new UserResponse(
                user.getId(),
                user.getEmail(),
                user.getNickname(),
                user.getPhone(),
                user.getRole(),
                user.getBankName(),
                user.getBankAccount(),
                user.getAddress(),
                user.getUnpaidStrike(),
                user.isBidBlocked(),
                user.getCreatedAt()
        );
    }
}
