package com.rocketcrew.pocat.domain.user.service;

import com.rocketcrew.pocat.domain.user.dto.response.AdminUserResponse;
import com.rocketcrew.pocat.domain.user.dto.response.UserResponse;
import com.rocketcrew.pocat.domain.user.entity.User;
import com.rocketcrew.pocat.domain.user.repository.UserRepository;
import com.rocketcrew.pocat.global.cache.CacheNames;
import com.rocketcrew.pocat.global.cache.UserNicknameCacheService;
import com.rocketcrew.pocat.global.exception.common.ErrorCode;
import com.rocketcrew.pocat.global.exception.domain.UserException;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserQueryService {

    private final UserRepository userRepository;
    private final UserNicknameCacheService userNicknameCacheService;

    public User getUserEntity(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new UserException(ErrorCode.USER_NOT_FOUND));
    }

    @Cacheable(value = CacheNames.USER_PROFILE, key = "#userId", sync = true)
    public UserResponse getUserById(Long userId) {
        User user = getUserEntity(userId);
        return UserResponse.from(user);
    }

    public Page<AdminUserResponse> getAllUsers(String keyword, Boolean isBidBlocked, Pageable pageable) {
        return userRepository.searchUsers(keyword, isBidBlocked, pageable)
                .map(AdminUserResponse::from);
    }

    public Map<Long, String> getNicknamesByUserIds(List<Long> ids) {
        return userNicknameCacheService.getNicknames(ids);
    }

    @Cacheable(value = CacheNames.USER_BID_BLOCKED, key = "#userId", sync = true)
    public boolean isBidBlocked(Long userId) {
        return userRepository.findById(userId)
                .map(User::isBidBlocked)
                .orElse(false);
    }
}
