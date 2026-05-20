package com.rocketcrew.pocat.domain.user.service;

import com.rocketcrew.pocat.domain.user.dto.request.RegisterBillingKeyRequest;
import com.rocketcrew.pocat.domain.user.dto.request.UpdateBillingKeyRequest;
import com.rocketcrew.pocat.domain.user.dto.request.UpdateBankRequest;
import com.rocketcrew.pocat.domain.user.dto.request.UpdateUserRequest;
import com.rocketcrew.pocat.domain.user.dto.response.UserResponse;
import com.rocketcrew.pocat.domain.user.entity.User;
import com.rocketcrew.pocat.domain.user.repository.UserRepository;
import com.rocketcrew.pocat.global.exception.common.ErrorCode;
import com.rocketcrew.pocat.global.exception.domain.UserException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class UserCommandService {

    private final UserRepository userRepository;

    public UserResponse updateUser(Long userId, UpdateUserRequest request) {
        User user = findUserOrThrow(userId);
        validateIfPresent(request.nickname());
        validateIfPresent(request.phone());
        validateIfPresent(request.address());
        String nickname = request.nickname() != null ? request.nickname() : user.getNickname();
        String phone = request.phone() != null ? request.phone() : user.getPhone();
        String address = request.address() != null ? request.address() : user.getAddress();
        user.updateProfile(nickname, phone, address);
        return UserResponse.from(user);
    }

    public void updateBank(Long userId, UpdateBankRequest request) {
        User user = findUserOrThrow(userId);
        user.updateBank(request.bankName(), request.bankAccount());
    }

    public void registerBillingKey(Long userId, RegisterBillingKeyRequest request) {
        int updated = userRepository.updateBillingKeyIfNull(userId, request.billingKey());
        if (updated == 0) {
            if (!userRepository.existsById(userId)) {
                throw new UserException(ErrorCode.USER_NOT_FOUND);
            }
            throw new UserException(ErrorCode.BILLING_KEY_ALREADY_EXISTS);
        }
    }

    public void deleteBillingKey(Long userId) {
        User user = findUserOrThrow(userId);
        if (user.getBillingKey() == null) {
            throw new UserException(ErrorCode.BILLING_KEY_NOT_FOUND);
        }
        user.deleteBillingKey();
    }

    public void updateBillingKey(Long userId, UpdateBillingKeyRequest request) {
        User user = findUserOrThrow(userId);
        if (user.getBillingKey() == null) {
            throw new UserException(ErrorCode.BILLING_KEY_NOT_FOUND);
        }
        user.registerBillingKey(request.billingKey());
    }

    private User findUserOrThrow(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new UserException(ErrorCode.USER_NOT_FOUND));
    }

    private void validateIfPresent(String value) {
        if (value != null && value.isBlank()) {
            throw new UserException(ErrorCode.INVALID_CONTENT);
        }
    }
}
