package com.rocketcrew.pocat.domain.ai.assistant.entity;

import com.rocketcrew.pocat.global.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;

/**
 * AI 채팅 메시지 엔티티.
 * 멀티턴 대화의 개별 메시지 저장.
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Builder
@AllArgsConstructor
@Entity
@Table(name = "ai_chat_messages",
        indexes = {
                @Index(name = "idx_ai_chat_message_session_id", columnList = "ai_chat_session_id"),
                @Index(name = "idx_ai_chat_message_created_at", columnList = "created_at")
        }
)
@SQLDelete(sql = "UPDATE ai_chat_messages SET deleted_at = NOW() WHERE id = ?")
@SQLRestriction("deleted_at IS NULL")
public class AiChatMessage extends BaseEntity {

    @Column(name = "ai_chat_session_id", nullable = false)
    private Long aiChatSessionId;

    @Column(name = "role", nullable = false, length = 20)
    private String role;  // user, assistant, system

    @Column(name = "content", nullable = false, columnDefinition = "LONGTEXT")
    private String content;

    @Column(name = "token_count", nullable = false)
    private Integer tokenCount;
}
