package com.rocketcrew.pocat.domain.user.dto.response;

import com.rocketcrew.pocat.domain.user.enums.UserRole;
import com.rocketcrew.pocat.domain.user.entity.User;

import java.time.LocalDateTime;

public record UserResponse(
        Long id,
        String email,
        String nickname,
        String phone,
        UserRole userRole,
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
                user.getUserRole(),
                user.getBankName(),
                user.getBankAccount(),
                user.getAddress(),
                user.getUnpaidStrike(),
                user.isBidBlocked(),
                user.getCreatedAt()
        );
    }
}
