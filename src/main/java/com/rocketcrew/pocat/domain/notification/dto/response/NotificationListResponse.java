package com.rocketcrew.pocat.domain.notification.dto.response;

import java.util.List;

public record NotificationListResponse(
        List<NotificationResponse> content,
        Long nextCursor,   // 다음 페이지 요청 시 사용
        boolean hasNext    // 다음 페이지 존재 여부
) {}
