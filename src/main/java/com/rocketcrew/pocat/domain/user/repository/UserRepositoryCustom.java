package com.rocketcrew.pocat.domain.user.repository;

import com.rocketcrew.pocat.domain.user.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface UserRepositoryCustom {
    Page<User> searchUsers(String keyword, Boolean isBidBlocked, Pageable pageable);
}
