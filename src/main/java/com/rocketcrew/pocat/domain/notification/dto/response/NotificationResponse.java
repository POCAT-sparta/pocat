package com.rocketcrew.pocat.domain.notification.dto.response;

import com.fasterxml.jackson.annotation.JsonRawValue;
import com.rocketcrew.pocat.domain.notification.entity.Notification;

import java.time.LocalDateTime;

public record NotificationResponse(
        Long notificationId,
        String type,
        String message,
        boolean isRead,
        @JsonRawValue String relatedData,
        LocalDateTime createdAt
) {
    public static NotificationResponse from(Notification notification) {
        return new NotificationResponse(
                notification.getId(),
                notification.getType().name(),
                notification.getMessage(),
                notification.isRead(),
                notification.getRelatedData(),
                notification.getCreatedAt()
        );
    }
}
