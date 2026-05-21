package com.rocketcrew.pocat.domain.notification.dto.event;

import com.rocketcrew.pocat.domain.notification.enums.NotificationType;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public class NotificationSendEvent {
    private final Long userId;
    private final NotificationType type;
    private final String message;
    private final Object relatedData;
}
