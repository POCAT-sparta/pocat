package com.rocketcrew.pocat.domain.card.entity;

import com.rocketcrew.pocat.global.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Builder
@AllArgsConstructor
@Entity
@Table(name = "cards")
public class Card extends BaseEntity {

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "tcgdex_id", length = 100)
    private String tcgdexId;

    @Column(nullable = false)
    private String name;

    @Column(length = 100)
    private String series;

    @Column(name = "set_name", length = 100)
    private String setName;

    @Column(name = "card_number", length = 20)
    private String cardNumber;

    @Column(length = 50)
    private String rarity;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private CardGrade grade;

    @Column(name = "image_url", length = 500)
    private String imageUrl;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private CardSource source;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private CardStatus status;

    public void update(String tcgdexId, String name, String series, String setName,
                       String cardNumber, String rarity, CardGrade grade,
                       String imageUrl, CardSource source) {
        this.tcgdexId = tcgdexId;
        this.name = name;
        this.series = series;
        this.setName = setName;
        this.cardNumber = cardNumber;
        this.rarity = rarity;
        this.grade = grade;
        this.imageUrl = imageUrl;
        this.source = source;
    }
}
