package com.rocketcrew.pocat.domain.user.dto.response;

import com.rocketcrew.pocat.domain.user.entity.User;
import com.rocketcrew.pocat.domain.user.enums.UserRole;

import java.time.LocalDateTime;

public record AdminUserResponse(
        Long id,
        String email,
        String nickname,
        String phone,
        UserRole role,
        String address,
        int unpaidStrike,
        boolean isBidBlocked,
        boolean hasBillingKey,
        LocalDateTime createdAt
) {
    public static AdminUserResponse from(User user) {
        return new AdminUserResponse(
                user.getId(),
                user.getEmail(),
                user.getNickname(),
                maskPhone(user.getPhone()),
                user.getUserRole(),
                user.getAddress(),
                user.getUnpaidStrike(),
                user.isBidBlocked(),
                user.getBillingKey() != null,
                user.getCreatedAt()
        );
    }

    private static String maskPhone(String phone) {
        if (phone == null || phone.length() < 4) return phone;
        return "***-****-" + phone.substring(phone.length() - 4);
    }
}
