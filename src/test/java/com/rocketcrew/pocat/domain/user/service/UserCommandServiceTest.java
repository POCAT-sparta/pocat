package com.rocketcrew.pocat.domain.user.service;

import com.rocketcrew.pocat.domain.user.dto.request.RegisterBillingKeyRequest;
import com.rocketcrew.pocat.domain.user.dto.request.UpdateBillingKeyRequest;
import com.rocketcrew.pocat.domain.user.dto.request.UpdateUserRequest;
import com.rocketcrew.pocat.domain.user.dto.response.UserResponse;
import com.rocketcrew.pocat.domain.user.entity.User;
import com.rocketcrew.pocat.domain.user.enums.UserRole;
import com.rocketcrew.pocat.domain.user.repository.UserRepository;
import com.rocketcrew.pocat.global.cache.UserNicknameCacheService;
import com.rocketcrew.pocat.global.exception.common.ErrorCode;
import com.rocketcrew.pocat.global.exception.domain.UserException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class UserCommandServiceTest {

    @InjectMocks
    private UserCommandService userCommandService;

    @Mock
    private UserRepository userRepository;

    @Mock
    private UserNicknameCacheService userNicknameCacheService;

    private User testUser;

    @BeforeEach
    void setUp() {
        testUser = User.builder()
                .email("test@example.com")
                .password("encodedPassword")
                .nickname("tester")
                .phone("010-1234-5678")
                .userRole(UserRole.USER)
                .build();
        ReflectionTestUtils.setField(testUser, "id", 1L);
    }

    @Nested
    @DisplayName("updateUser()")
    class UpdateUser {

        @Test
        @DisplayName("성공: 닉네임/전화번호/주소 업데이트")
        void success() {
            // given
            UpdateUserRequest request = new UpdateUserRequest("newNick", "010-9999-9999", "서울시");
            given(userRepository.findById(1L)).willReturn(Optional.of(testUser));

            // when
            UserResponse response = userCommandService.updateUser(1L, request);

            // then
            assertThat(response.nickname()).isEqualTo("newNick");
            assertThat(response.phone()).isEqualTo("010-9999-9999");
            assertThat(response.address()).isEqualTo("서울시");
        }

        @Test
        @DisplayName("실패: 유저 없음 → USER_NOT_FOUND")
        void fail_userNotFound() {
            // given
            UpdateUserRequest request = new UpdateUserRequest("newNick", null, null);
            given(userRepository.findById(999L)).willReturn(Optional.empty());

            // when & then
            assertThatThrownBy(() -> userCommandService.updateUser(999L, request))
                    .isInstanceOf(UserException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.USER_NOT_FOUND);
        }

        @Test
        @DisplayName("실패: 빈 닉네임 → INVALID_CONTENT")
        void fail_blankNickname() {
            // given
            UpdateUserRequest request = new UpdateUserRequest("   ", null, null);
            given(userRepository.findById(1L)).willReturn(Optional.of(testUser));

            // when & then
            assertThatThrownBy(() -> userCommandService.updateUser(1L, request))
                    .isInstanceOf(UserException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_CONTENT);
        }
    }

    @Nested
    @DisplayName("registerBillingKey()")
    class RegisterBillingKey {

        @Test
        @DisplayName("성공: 빌링키 등록")
        void success() {
            // given
            RegisterBillingKeyRequest request = new RegisterBillingKeyRequest("billingKey-abc");
            given(userRepository.updateBillingKeyIfNull(1L, "billingKey-abc")).willReturn(1);

            // when
            userCommandService.registerBillingKey(1L, request);

            // then
            verify(userRepository).updateBillingKeyIfNull(1L, "billingKey-abc");
        }

        @Test
        @DisplayName("실패: 유저 없음 → USER_NOT_FOUND")
        void fail_userNotFound() {
            // given
            RegisterBillingKeyRequest request = new RegisterBillingKeyRequest("billingKey-abc");
            given(userRepository.updateBillingKeyIfNull(999L, "billingKey-abc")).willReturn(0);
            given(userRepository.existsById(999L)).willReturn(false);

            // when & then
            assertThatThrownBy(() -> userCommandService.registerBillingKey(999L, request))
                    .isInstanceOf(UserException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.USER_NOT_FOUND);
        }

        @Test
        @DisplayName("실패: 이미 빌링키 존재 → BILLING_KEY_ALREADY_EXISTS")
        void fail_billingKeyAlreadyExists() {
            // given
            RegisterBillingKeyRequest request = new RegisterBillingKeyRequest("billingKey-abc");
            given(userRepository.updateBillingKeyIfNull(1L, "billingKey-abc")).willReturn(0);
            given(userRepository.existsById(1L)).willReturn(true);

            // when & then
            assertThatThrownBy(() -> userCommandService.registerBillingKey(1L, request))
                    .isInstanceOf(UserException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.BILLING_KEY_ALREADY_EXISTS);
        }
    }

    @Nested
    @DisplayName("deleteBillingKey()")
    class DeleteBillingKey {

        @Test
        @DisplayName("성공: 빌링키 삭제")
        void success() {
            // given
            ReflectionTestUtils.setField(testUser, "billingKey", "existing-key");
            given(userRepository.findById(1L)).willReturn(Optional.of(testUser));

            // when
            userCommandService.deleteBillingKey(1L);

            // then
            assertThat(testUser.getBillingKey()).isNull();
        }

        @Test
        @DisplayName("실패: 유저 없음 → USER_NOT_FOUND")
        void fail_userNotFound() {
            // given
            given(userRepository.findById(999L)).willReturn(Optional.empty());

            // when & then
            assertThatThrownBy(() -> userCommandService.deleteBillingKey(999L))
                    .isInstanceOf(UserException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.USER_NOT_FOUND);
        }

        @Test
        @DisplayName("실패: 등록된 빌링키 없음 → BILLING_KEY_NOT_FOUND")
        void fail_billingKeyNotFound() {
            // given — billingKey is null by default
            given(userRepository.findById(1L)).willReturn(Optional.of(testUser));

            // when & then
            assertThatThrownBy(() -> userCommandService.deleteBillingKey(1L))
                    .isInstanceOf(UserException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.BILLING_KEY_NOT_FOUND);
        }
    }

    @Nested
    @DisplayName("updateBillingKey()")
    class UpdateBillingKey {

        @Test
        @DisplayName("성공: 빌링키 업데이트")
        void success() {
            // given
            ReflectionTestUtils.setField(testUser, "billingKey", "old-key");
            UpdateBillingKeyRequest request = new UpdateBillingKeyRequest("new-billing-key");
            given(userRepository.findById(1L)).willReturn(Optional.of(testUser));

            // when
            userCommandService.updateBillingKey(1L, request);

            // then
            assertThat(testUser.getBillingKey()).isEqualTo("new-billing-key");
        }

        @Test
        @DisplayName("실패: 유저 없음 → USER_NOT_FOUND")
        void fail_userNotFound() {
            // given
            UpdateBillingKeyRequest request = new UpdateBillingKeyRequest("new-billing-key");
            given(userRepository.findById(999L)).willReturn(Optional.empty());

            // when & then
            assertThatThrownBy(() -> userCommandService.updateBillingKey(999L, request))
                    .isInstanceOf(UserException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.USER_NOT_FOUND);
        }

        @Test
        @DisplayName("실패: 기존 빌링키 없음 → BILLING_KEY_NOT_FOUND")
        void fail_billingKeyNotFound() {
            // given — billingKey is null by default
            UpdateBillingKeyRequest request = new UpdateBillingKeyRequest("new-billing-key");
            given(userRepository.findById(1L)).willReturn(Optional.of(testUser));

            // when & then
            assertThatThrownBy(() -> userCommandService.updateBillingKey(1L, request))
                    .isInstanceOf(UserException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.BILLING_KEY_NOT_FOUND);
        }
    }
}
