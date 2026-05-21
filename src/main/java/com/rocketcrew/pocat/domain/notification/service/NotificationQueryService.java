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
                ? notificationRepository.findTop21ByUserIdAndIsReadFalseOrderByIdDesc(userId)
                : notificationRepository.findTop21ByUserIdAndIsReadFalseAndIdLessThanOrderByIdDesc(userId, cursor);

        boolean hasNext = notifications.size() == 21;
        List<Notification> page = hasNext ? notifications.subList(0, 20) : notifications;
        Long nextCursor = hasNext ? page.get(page.size() - 1).getId() : null;

        List<NotificationResponse> content = page.stream()
                .map(NotificationResponse::from)
                .toList();

        return new NotificationListResponse(content, nextCursor, hasNext);
    }
}
