package com.rocketcrew.pocat.domain.notification.service;

import com.rocketcrew.pocat.domain.notification.dto.response.NotificationListResponse;
import com.rocketcrew.pocat.domain.notification.entity.Notification;
import com.rocketcrew.pocat.domain.notification.enums.NotificationType;
import com.rocketcrew.pocat.domain.notification.repository.NotificationRepository;
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

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("NotificationQueryService")
class NotificationQueryServiceTest {

    @InjectMocks
    private NotificationQueryService notificationQueryService;

    @Mock
    private NotificationRepository notificationRepository;

    private Notification buildNotification(long id) {
        Notification n = Notification.create(1L, NotificationType.BID_OUTBID, "메시지", null);
        ReflectionTestUtils.setField(n, "id", id);
        return n;
    }

    @Nested
    @DisplayName("getNotifications()")
    class GetNotifications {

        @Test
        @DisplayName("cursor=null, 20개 이하 반환 → hasNext=false, nextCursor=null")
        void noCursor_lessThan21_hasNextFalse() {
            List<Notification> list = new ArrayList<>();
            for (long i = 1; i <= 5; i++) {
                list.add(buildNotification(i));
            }
            given(notificationRepository.findTop21ByUserIdAndIsReadFalseOrderByIdDesc(1L))
                    .willReturn(list);

            NotificationListResponse response = notificationQueryService.getNotifications(1L, null);

            assertThat(response.hasNext()).isFalse();
            assertThat(response.nextCursor()).isNull();
            assertThat(response.content()).hasSize(5);
        }

        @Test
        @DisplayName("cursor=null, 21개 반환 → hasNext=true, nextCursor 존재")
        void noCursor_21items_hasNextTrue() {
            List<Notification> list = new ArrayList<>();
            for (long i = 21; i >= 1; i--) {
                list.add(buildNotification(i));
            }
            given(notificationRepository.findTop21ByUserIdAndIsReadFalseOrderByIdDesc(1L))
                    .willReturn(list);

            NotificationListResponse response = notificationQueryService.getNotifications(1L, null);

            assertThat(response.hasNext()).isTrue();
            assertThat(response.nextCursor()).isNotNull();
            assertThat(response.content()).hasSize(20);
        }

        @Test
        @DisplayName("cursor!=null, cursor 기준 조회")
        void withCursor_success() {
            List<Notification> list = new ArrayList<>();
            for (long i = 10; i >= 1; i--) {
                list.add(buildNotification(i));
            }
            given(notificationRepository.findTop21ByUserIdAndIsReadFalseAndIdLessThanOrderByIdDesc(1L, 11L))
                    .willReturn(list);

            NotificationListResponse response = notificationQueryService.getNotifications(1L, 11L);

            assertThat(response.hasNext()).isFalse();
            assertThat(response.content()).hasSize(10);
        }
    }
}
