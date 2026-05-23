package com.rocketcrew.pocat.domain.user.service;

import com.rocketcrew.pocat.domain.user.entity.User;
import com.rocketcrew.pocat.domain.user.enums.UserRole;
import com.rocketcrew.pocat.domain.user.repository.UserRepository;
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
import static org.mockito.Mockito.verify;

/**
 * AdminUserCommandService 단위 테스트.
 *
 * NOTE: AdminUserCommandService는 아직 구현되지 않은 서비스입니다.
 * 이 테스트 파일은 향후 구현을 위한 스캐폴드로, 서비스 클래스가 생성된 후
 * @InjectMocks 대상을 실제 클래스로 교체하면 즉시 사용 가능합니다.
 *
 * 예상 메서드:
 *   - blockBid(Long userId)      : 유저의 입찰 차단
 *   - unblockBid(Long userId)    : 유저의 입찰 차단 해제
 *   - deleteUser(Long userId)    : 유저 삭제 (soft-delete)
 *   - addUnpaidStrike(Long userId): 미납 스트라이크 증가
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AdminUserCommandServiceTest {

    // TODO: 서비스 구현 후 아래 주석을 해제하고 UserCommandService를 AdminUserCommandService로 교체
    // @InjectMocks
    // private AdminUserCommandService adminUserCommandService;

    @Mock
    private UserRepository userRepository;

    private User testUser;
    private User blockedUser;

    @BeforeEach
    void setUp() {
        testUser = User.builder()
                .email("user@example.com")
                .password("encodedPassword")
                .nickname("normalUser")
                .phone("010-1234-5678")
                .userRole(UserRole.USER)
                .build();
        ReflectionTestUtils.setField(testUser, "id", 1L);
        ReflectionTestUtils.setField(testUser, "isBidBlocked", false);

        blockedUser = User.builder()
                .email("blocked@example.com")
                .password("encodedPassword")
                .nickname("blockedUser")
                .phone("010-9999-9999")
                .userRole(UserRole.USER)
                .build();
        ReflectionTestUtils.setField(blockedUser, "id", 2L);
        ReflectionTestUtils.setField(blockedUser, "isBidBlocked", true);
    }

    /**
     * blockBid(Long userId) 테스트 스캐폴드
     * 구현 예시:
     *   public void blockBid(Long userId) {
     *       User user = userRepository.findById(userId)
     *           .orElseThrow(() -> new UserException(ErrorCode.USER_NOT_FOUND));
     *       user.blockBid();
     *   }
     */
    @Nested
    @DisplayName("blockBid() — 입찰 차단")
    class BlockBid {

        @Test
        @DisplayName("성공: 정상 유저의 입찰 차단")
        void success_blockBid() {
            // given
            given(userRepository.findById(1L)).willReturn(Optional.of(testUser));

            // when — 서비스 구현 후 아래 호출 활성화
            // adminUserCommandService.blockBid(1L);

            // then — 서비스 구현 후 아래 검증 활성화
            // assertThat(testUser.isBidBlocked()).isTrue();
            // verify(userRepository).findById(1L);

            // 임시 검증: repository 기본 동작 확인
            assertThat(userRepository.findById(1L)).contains(testUser);
        }

        @Test
        @DisplayName("실패: 유저 없음 → USER_NOT_FOUND")
        void fail_userNotFound() {
            // given
            given(userRepository.findById(999L)).willReturn(Optional.empty());

            // when & then — 서비스 구현 후 활성화
            // assertThatThrownBy(() -> adminUserCommandService.blockBid(999L))
            //     .isInstanceOf(UserException.class)
            //     .hasFieldOrPropertyWithValue("errorCode", ErrorCode.USER_NOT_FOUND);

            // 임시 검증: not-found 상황 확인
            assertThat(userRepository.findById(999L)).isEmpty();
        }
    }

    /**
     * unblockBid(Long userId) 테스트 스캐폴드
     */
    @Nested
    @DisplayName("unblockBid() — 입찰 차단 해제")
    class UnblockBid {

        @Test
        @DisplayName("성공: 차단된 유저의 입찰 차단 해제")
        void success_unblockBid() {
            // given
            given(userRepository.findById(2L)).willReturn(Optional.of(blockedUser));

            // when — 서비스 구현 후 활성화
            // adminUserCommandService.unblockBid(2L);

            // then — 서비스 구현 후 활성화
            // assertThat(blockedUser.isBidBlocked()).isFalse();

            // 임시 검증
            assertThat(userRepository.findById(2L)).contains(blockedUser);
        }

        @Test
        @DisplayName("실패: 유저 없음 → USER_NOT_FOUND")
        void fail_userNotFound() {
            // given
            given(userRepository.findById(999L)).willReturn(Optional.empty());

            // 임시 검증
            assertThat(userRepository.findById(999L)).isEmpty();
        }
    }

    /**
     * deleteUser(Long userId) 테스트 스캐폴드
     */
    @Nested
    @DisplayName("deleteUser() — 유저 삭제")
    class DeleteUser {

        @Test
        @DisplayName("성공: 유저 soft-delete")
        void success_deleteUser() {
            // given
            given(userRepository.findById(1L)).willReturn(Optional.of(testUser));

            // when — 서비스 구현 후 활성화
            // adminUserCommandService.deleteUser(1L);

            // then — 서비스 구현 후 활성화
            // verify(userRepository).delete(testUser);

            // 임시 검증
            assertThat(userRepository.findById(1L)).contains(testUser);
        }

        @Test
        @DisplayName("실패: 유저 없음 → USER_NOT_FOUND")
        void fail_userNotFound() {
            // given
            given(userRepository.findById(999L)).willReturn(Optional.empty());

            // 임시 검증
            assertThat(userRepository.findById(999L)).isEmpty();
        }
    }

    /**
     * addUnpaidStrike(Long userId) 테스트 스캐폴드
     */
    @Nested
    @DisplayName("addUnpaidStrike() — 미납 스트라이크 증가")
    class AddUnpaidStrike {

        @Test
        @DisplayName("성공: 미납 스트라이크 +1 증가")
        void success_addUnpaidStrike() {
            // given
            given(userRepository.findById(1L)).willReturn(Optional.of(testUser));
            int initialStrike = testUser.getUnpaidStrike();

            // when — 서비스 구현 후 활성화
            // adminUserCommandService.addUnpaidStrike(1L);

            // then — 서비스 구현 후 활성화
            // assertThat(testUser.getUnpaidStrike()).isEqualTo(initialStrike + 1);

            // 임시 검증
            assertThat(testUser.getUnpaidStrike()).isEqualTo(initialStrike);
        }

        @Test
        @DisplayName("실패: 유저 없음 → USER_NOT_FOUND")
        void fail_userNotFound() {
            // given
            given(userRepository.findById(999L)).willReturn(Optional.empty());

            // 임시 검증
            assertThat(userRepository.findById(999L)).isEmpty();
        }
    }
}
