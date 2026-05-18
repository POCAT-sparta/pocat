package com.rocketcrew.pocat.domain.like.entity;

import com.rocketcrew.pocat.global.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Builder
@AllArgsConstructor
@Entity
@Table(name = "likes")
public class Like extends BaseEntity {

    @Column(nullable = false)
    private Long userId;

    private Long auctionId;
}
