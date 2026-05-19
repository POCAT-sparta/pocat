package com.rocketcrew.pocat.domain.notification.entity;

import com.rocketcrew.pocat.global.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.SQLDelete;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Builder
@AllArgsConstructor
@Entity
@Table(name = "notifications")
@SQLDelete(sql = "UPDATE notifications SET deleted_at = NOW() WHERE id = ?")
public class Notification extends BaseEntity {

    @Column(nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private NotificationType type;

    @Column(nullable = false)
    private String title;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String message;

    @Column(nullable = false)
    private boolean isRead;

    public void markAsRead() {
        this.isRead = true;
    }
}
