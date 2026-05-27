package com.rocketcrew.pocat.domain.ai.session.entity;

import com.rocketcrew.pocat.global.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "ai_chat_session", indexes = {
        @Index(name = "idx_ai_chat_session_user_id", columnList = "user_id"),
        @Index(name = "idx_ai_chat_session_last_active", columnList = "last_active_at")
})
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AiChatSession extends BaseEntity {

    @Column(nullable = false, length = 36, unique = true)
    private String sessionUuid;

    @Column(nullable = false)
    private Long userId;

    @Column(nullable = false)
    private Long totalTokens;

    @Column(nullable = false)
    private LocalDateTime lastActiveAt;

    @Column(nullable = false)
    private Boolean isExpired;
}
