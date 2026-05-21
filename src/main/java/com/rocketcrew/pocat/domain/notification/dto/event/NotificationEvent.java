package com.rocketcrew.pocat.domain.notification.dto.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NotificationEvent {
    private Long notificationId;
    private Long userId;
    private String type;
    private String message;
    private Object relatedData;
    private LocalDateTime createdAt;
}
