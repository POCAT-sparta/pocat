package com.rocketcrew.pocat.domain.like.entity;

import com.rocketcrew.pocat.global.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.SQLDelete;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Builder
@AllArgsConstructor
@Entity
@Table(name = "likes")
@SQLDelete(sql = "UPDATE likes SET deleted_at = NOW() WHERE id = ?")
public class Like extends BaseEntity {

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "auction_id")
    private Long auctionId;
}
