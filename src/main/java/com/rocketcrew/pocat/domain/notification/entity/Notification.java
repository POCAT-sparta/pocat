package com.rocketcrew.pocat.domain.notification.entity;

import com.rocketcrew.pocat.domain.notification.enums.NotificationType;
import com.rocketcrew.pocat.global.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Builder
@AllArgsConstructor
@Entity
@Table(name = "notifications")
@SQLDelete(sql = "UPDATE notifications SET deleted_at = NOW() WHERE id = ?")
@SQLRestriction("deleted_at IS NULL")
public class Notification extends BaseEntity {

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 50)
    private NotificationType type;

    @Column(name = "message", nullable = false)
    private String message;

    @Column(name = "is_read", nullable = false)
    private boolean isRead;

    // 관련 데이터 (auctionId, orderUid 등 JSON으로 저장)
    @Column(name = "related_data")
    private String relatedData;

    public void read() {
        this.isRead = true;
    }

    public static Notification create(Long userId, NotificationType type,
                                      String message, String relatedData) {
        return Notification.builder()
                .userId(userId)
                .type(type)
                .message(message)
                .isRead(false)
                .relatedData(relatedData)
                .build();
    }
}
