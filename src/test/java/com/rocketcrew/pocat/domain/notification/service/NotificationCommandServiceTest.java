package com.rocketcrew.pocat.domain.notification.service;

import com.rocketcrew.pocat.domain.notification.dto.response.NotificationResponse;
import com.rocketcrew.pocat.domain.notification.entity.Notification;
import com.rocketcrew.pocat.domain.notification.enums.NotificationType;
import com.rocketcrew.pocat.domain.notification.repository.NotificationRepository;
import com.rocketcrew.pocat.global.exception.common.ErrorCode;
import com.rocketcrew.pocat.global.exception.domain.NotificationException;
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
import static org.mockito.BDDMockito.willDoNothing;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("NotificationCommandService")
class NotificationCommandServiceTest {

    @InjectMocks
    private NotificationCommandService notificationCommandService;

    @Mock
    private NotificationRepository notificationRepository;

    @Mock
    private org.springframework.kafka.core.KafkaTemplate<String, String> kafkaTemplate;

    @Mock
    private com.fasterxml.jackson.databind.ObjectMapper objectMapper;

    private Notification notification;

    @BeforeEach
    void setUp() {
        notification = Notification.create(1L, NotificationType.BID_OUTBID, "입찰이 초과되었습니다.", null);
        ReflectionTestUtils.setField(notification, "id", 10L);
    }

    @Nested
    @DisplayName("read()")
    class Read {

        @Test
        @DisplayName("성공: 알림 읽음 처리")
        void success() {
            given(notificationRepository.findById(10L)).willReturn(Optional.of(notification));

            NotificationResponse response = notificationCommandService.read(1L, 10L);

            assertThat(response).isNotNull();
            assertThat(response.notificationId()).isEqualTo(10L);
            assertThat(notification.isRead()).isTrue();
        }

        @Test
        @DisplayName("실패: 알림 없음 - NOTIFICATION_NOT_FOUND")
        void notFound() {
            given(notificationRepository.findById(999L)).willReturn(Optional.empty());

            assertThatThrownBy(() -> notificationCommandService.read(1L, 999L))
                    .isInstanceOf(NotificationException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.NOTIFICATION_NOT_FOUND);
        }

        @Test
        @DisplayName("실패: 접근 권한 없음 - NOTIFICATION_ACCESS_DENIED")
        void accessDenied() {
            given(notificationRepository.findById(10L)).willReturn(Optional.of(notification));

            assertThatThrownBy(() -> notificationCommandService.read(99L, 10L))
                    .isInstanceOf(NotificationException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.NOTIFICATION_ACCESS_DENIED);
        }
    }

    @Nested
    @DisplayName("readAll()")
    class ReadAll {

        @Test
        @DisplayName("성공: 전체 읽음 처리 호출")
        void success() {
            willDoNothing().given(notificationRepository).markAllReadByUserId(1L);

            notificationCommandService.readAll(1L);

            verify(notificationRepository).markAllReadByUserId(1L);
        }
    }

    @Nested
    @DisplayName("delete()")
    class Delete {

        @Test
        @DisplayName("성공: 알림 삭제")
        void success() {
            given(notificationRepository.findById(10L)).willReturn(Optional.of(notification));
            willDoNothing().given(notificationRepository).delete(notification);

            notificationCommandService.delete(1L, 10L);

            verify(notificationRepository).delete(notification);
        }

        @Test
        @DisplayName("실패: 알림 없음 - NOTIFICATION_NOT_FOUND")
        void notFound() {
            given(notificationRepository.findById(999L)).willReturn(Optional.empty());

            assertThatThrownBy(() -> notificationCommandService.delete(1L, 999L))
                    .isInstanceOf(NotificationException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.NOTIFICATION_NOT_FOUND);
        }

        @Test
        @DisplayName("실패: 접근 권한 없음 - NOTIFICATION_ACCESS_DENIED")
        void accessDenied() {
            given(notificationRepository.findById(10L)).willReturn(Optional.of(notification));

            assertThatThrownBy(() -> notificationCommandService.delete(99L, 10L))
                    .isInstanceOf(NotificationException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.NOTIFICATION_ACCESS_DENIED);
        }
    }

    @Nested
    @DisplayName("deleteAll()")
    class DeleteAll {

        @Test
        @DisplayName("성공: 전체 소프트 삭제 호출")
        void success() {
            willDoNothing().given(notificationRepository).softDeleteAllByUserId(1L);

            notificationCommandService.deleteAll(1L);

            verify(notificationRepository).softDeleteAllByUserId(1L);
        }
    }
}
