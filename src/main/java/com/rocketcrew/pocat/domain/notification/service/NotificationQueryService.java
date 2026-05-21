package com.rocketcrew.pocat.domain.notification.service;

import com.rocketcrew.pocat.domain.notification.dto.response.NotificationListResponse;
import com.rocketcrew.pocat.domain.notification.dto.response.NotificationResponse;
import com.rocketcrew.pocat.domain.notification.entity.Notification;
import com.rocketcrew.pocat.domain.notification.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class NotificationQueryService {

    private final NotificationRepository notificationRepository;

    public NotificationListResponse getNotifications(Long userId, Long cursor) {
        List<Notification> notifications = (cursor == null)
                ? notificationRepository.findTop20ByUserIdAndIsReadFalseOrderByCreatedAtDesc(userId)
                : notificationRepository.findTop20ByUserIdAndIsReadFalseAndIdLessThanOrderByCreatedAtDesc(userId, cursor);

        boolean hasNext = notifications.size() == 20;
        Long nextCursor = hasNext ? notifications.get(notifications.size() - 1).getId() : null;

        List<NotificationResponse> content = notifications.stream()
                .map(NotificationResponse::from)
                .toList();

        return new NotificationListResponse(content, nextCursor, hasNext);
    }
}
