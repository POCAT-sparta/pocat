package com.rocketcrew.pocat.domain.community.tradepost.entity;

import com.rocketcrew.pocat.global.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
@Entity
@Table(name = "trade_posts")
public class TradePost extends BaseEntity {

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(nullable = false)
    private String title;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    @Column(nullable = false)
    private Long price;

    @Column(columnDefinition = "TEXT")
    private String thumbnail;

    @Column(nullable = false)
    private int viewCount;

    public void update(String title, String content, Long price, String thumbnail) {
        this.title = title;
        this.content = content;
        this.price = price;
        this.thumbnail = thumbnail;
    }

    public void incrementViewCount() {
        this.viewCount++;
    }
}
