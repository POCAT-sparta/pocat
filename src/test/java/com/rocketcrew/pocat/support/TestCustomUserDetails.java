package com.rocketcrew.pocat.support;

import com.rocketcrew.pocat.global.security.CustomUserDetails;

public final class TestCustomUserDetails extends CustomUserDetails {
    public TestCustomUserDetails(Long userId, String role) {
        super(userId, role);
    }
}
