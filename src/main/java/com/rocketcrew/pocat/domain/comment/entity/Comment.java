package com.rocketcrew.pocat.domain.comment.entity;

import com.rocketcrew.pocat.global.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.SQLDelete;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Builder
@AllArgsConstructor
@Entity
@Table(name = "comments")
@SQLDelete(sql = "UPDATE comments SET deleted_at = NOW() WHERE id = ?")
public class Comment extends BaseEntity {

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "post_id", nullable = false)
    private Long postId;

    @Column(name = "parent_id")
    private Long parentId;

    @Column(name = "content", nullable = false, columnDefinition = "TEXT")
    private String content;

    public void update(String content) {
        this.content = content;
    }
}
