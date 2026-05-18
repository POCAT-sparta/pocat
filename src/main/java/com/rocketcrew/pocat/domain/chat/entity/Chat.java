package com.rocketcrew.pocat.domain.chat.entity;

import com.rocketcrew.pocat.domain.chat.entity.enums.ChatStatus;
import com.rocketcrew.pocat.global.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
@Entity
@Table(name = "chats")
public class Chat extends BaseEntity {

    @Column(name="owner_id", nullable = false)
    private Long ownerId;

    @Column(name="guest_id", nullable = false)
    private Long guestId;

    @Column(name="post_id", nullable = false)
    private Long postId;

    @Enumerated(EnumType.STRING)
    @Column(name="status", nullable = false, length = 20)
    private ChatStatus status;

    public void updateStatus(ChatStatus status) {
        this.status = status;
    }
}
