package com.rocketcrew.pocat.domain.user.service;

import com.rocketcrew.pocat.domain.user.entity.User;
import com.rocketcrew.pocat.domain.user.repository.UserRepository;
import com.rocketcrew.pocat.global.cache.CacheNames;
import com.rocketcrew.pocat.global.exception.common.ErrorCode;
import com.rocketcrew.pocat.global.exception.domain.UserException;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Caching;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class AdminUserCommandService {

    private final UserRepository userRepository;

    @PreAuthorize("hasRole('ADMIN')")
    @Caching(evict = {
            @CacheEvict(value = CacheNames.USER_BID_BLOCKED, key = "#userId"),
            @CacheEvict(value = CacheNames.USER_PROFILE, key = "#userId")
    })
    public void toggleBidBlock(Long userId, boolean blocked) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserException(ErrorCode.USER_NOT_FOUND));
        if (blocked) {
            user.block();
        } else {
            user.unblock();
        }
    }
}
