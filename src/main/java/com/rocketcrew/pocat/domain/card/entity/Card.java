package com.rocketcrew.pocat.domain.card.entity;

import com.rocketcrew.pocat.domain.card.entity.enums.CardCategory;
import com.rocketcrew.pocat.domain.card.entity.enums.CardGrade;
import com.rocketcrew.pocat.domain.card.entity.enums.CardSource;
import com.rocketcrew.pocat.domain.card.entity.enums.CardStatus;
import com.rocketcrew.pocat.global.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.SQLDelete;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Builder
@AllArgsConstructor
@Entity
@Table(name = "cards")
@SQLDelete(sql = "UPDATE cards SET deleted_at = NOW() WHERE id = ?")
public class Card extends BaseEntity {

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "tcgdex_id", length = 100)
    private String tcgdexId;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "series", length = 100)
    private String series;

    @Column(name = "set_id", length = 50)
    private String setId;

    @Column(name = "set_name", length = 100)
    private String setName;

    @Column(name = "card_number", length = 20)
    private String cardNumber;

    @Column(name = "rarity", length = 50)
    private String rarity;

    @Enumerated(EnumType.STRING)
    @Column(name = "category", length = 20)
    private CardCategory category;

    @Enumerated(EnumType.STRING)
    @Column(name = "grade", nullable = false, length = 20)
    private CardGrade grade;

    @Column(name = "image_url", length = 500)
    private String imageUrl;

    @Enumerated(EnumType.STRING)
    @Column(name = "source", nullable = false, length = 20)
    private CardSource source;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private CardStatus status;

    @Column(name = "reject_reason", columnDefinition = "TEXT")
    private String rejectReason;

    public void approve() {
        this.status = CardStatus.ACTIVE;
    }

    public void reject(String rejectReason) {
        this.status = CardStatus.REJECTED;
        this.rejectReason = rejectReason;
    }

    public void update(String tcgdexId, String name, String series, String setId, String setName,
                       String cardNumber, String rarity, CardCategory category, CardGrade grade,
                       String imageUrl, CardSource source) {
        this.tcgdexId = tcgdexId;
        this.name = name;
        this.series = series;
        this.setId = setId;
        this.setName = setName;
        this.cardNumber = cardNumber;
        this.rarity = rarity;
        this.category = category;
        this.grade = grade;
        this.imageUrl = imageUrl;
        this.source = source;
    }
}
