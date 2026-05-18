package com.rocketcrew.pocat.domain.chat.entity;

import com.rocketcrew.pocat.global.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.SQLDelete;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
@Entity
@Table(name = "chat_messages")
@SQLDelete(sql = "UPDATE chat_messages SET deleted_at = NOW() WHERE id = ?")
public class ChatMessage extends BaseEntity {

    @Column(name="sender_id", nullable = false)
    private Long senderId;

    @Column(name="chat_id", nullable = false)
    private Long chatId;

    @Column(name = "message", nullable = false, columnDefinition = "TEXT")
    private String message;

    @Column(name = "is_read", nullable = false)
    private boolean isRead;
}
