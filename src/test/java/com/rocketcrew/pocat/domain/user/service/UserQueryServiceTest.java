package com.rocketcrew.pocat.domain.user.service;

import com.rocketcrew.pocat.domain.user.dto.response.AdminUserResponse;
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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class UserQueryServiceTest {

    @InjectMocks
    private UserQueryService userQueryService;

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
    @DisplayName("getUserById()")
    class GetUserById {

        @Test
        @DisplayName("성공: userId로 UserResponse 반환")
        void success() {
            // given
            given(userRepository.findById(1L)).willReturn(Optional.of(testUser));

            // when
            UserResponse response = userQueryService.getUserById(1L);

            // then
            assertThat(response.id()).isEqualTo(1L);
            assertThat(response.email()).isEqualTo("test@example.com");
            assertThat(response.nickname()).isEqualTo("tester");
        }

        @Test
        @DisplayName("실패: 유저 없음 → USER_NOT_FOUND")
        void fail_userNotFound() {
            // given
            given(userRepository.findById(999L)).willReturn(Optional.empty());

            // when & then
            assertThatThrownBy(() -> userQueryService.getUserById(999L))
                    .isInstanceOf(UserException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.USER_NOT_FOUND);
        }
    }

    @Nested
    @DisplayName("getUserEntity()")
    class GetUserEntity {

        @Test
        @DisplayName("성공: userId로 User 엔티티 반환")
        void success() {
            // given
            given(userRepository.findById(1L)).willReturn(Optional.of(testUser));

            // when
            User result = userQueryService.getUserEntity(1L);

            // then
            assertThat(result.getId()).isEqualTo(1L);
            assertThat(result.getEmail()).isEqualTo("test@example.com");
        }

        @Test
        @DisplayName("실패: 유저 없음 → USER_NOT_FOUND")
        void fail_userNotFound() {
            // given
            given(userRepository.findById(999L)).willReturn(Optional.empty());

            // when & then
            assertThatThrownBy(() -> userQueryService.getUserEntity(999L))
                    .isInstanceOf(UserException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.USER_NOT_FOUND);
        }
    }

    @Nested
    @DisplayName("getAllUsers()")
    class GetAllUsers {

        @Test
        @DisplayName("성공: keyword/isBidBlocked 조건으로 유저 목록 반환")
        void success() {
            // given
            Pageable pageable = PageRequest.of(0, 10);
            Page<User> userPage = new PageImpl<>(List.of(testUser), pageable, 1);
            given(userRepository.searchUsers(any(), any(), eq(pageable))).willReturn(userPage);

            // when
            Page<AdminUserResponse> result = userQueryService.getAllUsers(null, null, pageable);

            // then
            assertThat(result.getTotalElements()).isEqualTo(1);
            assertThat(result.getContent().get(0).email()).isEqualTo("test@example.com");
        }

        @Test
        @DisplayName("성공: 빈 결과 반환")
        void success_emptyResult() {
            // given
            Pageable pageable = PageRequest.of(0, 10);
            Page<User> emptyPage = new PageImpl<>(List.of(), pageable, 0);
            given(userRepository.searchUsers(eq("nonexistent"), any(), eq(pageable))).willReturn(emptyPage);

            // when
            Page<AdminUserResponse> result = userQueryService.getAllUsers("nonexistent", null, pageable);

            // then
            assertThat(result.getTotalElements()).isEqualTo(0);
            assertThat(result.getContent()).isEmpty();
        }
    }

    @Nested
    @DisplayName("getNicknamesByUserIds()")
    class GetNicknamesByUserIds {

        @Test
        @DisplayName("성공: userId 목록으로 닉네임 맵 반환")
        void success() {
            // given
            given(userRepository.findAllById(List.of(1L))).willReturn(List.of(testUser));
            given(userNicknameCacheService.getNicknames(List.of(1L))).willReturn(Map.of(1L, "tester"));

            // when
            var nicknameMap = userQueryService.getNicknamesByUserIds(List.of(1L));

            // then
            assertThat(nicknameMap).containsEntry(1L, "tester");
        }
    }
}
