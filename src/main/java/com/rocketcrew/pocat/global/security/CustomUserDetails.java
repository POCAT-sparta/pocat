package com.rocketcrew.pocat.global.security;

import lombok.Getter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.io.Serial;
import java.io.Serializable;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

@Getter
public class CustomUserDetails implements UserDetails, Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private final Long userId;
    private final String role;

    protected CustomUserDetails(Long userId, String role) {
        Objects.requireNonNull(userId, "userId must not be null");
        Objects.requireNonNull(role, "role must not be null");
        if (role.isBlank()) {
            throw new IllegalArgumentException("role must not be blank");
        }
        this.userId = userId;
        this.role = role.startsWith("ROLE_") ? role : "ROLE_" + role;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority(role));
    }

    @Override
    public String getPassword() {
        return null;
    }

    @Override
    public String getUsername() {
        return String.valueOf(userId);
    }
}
