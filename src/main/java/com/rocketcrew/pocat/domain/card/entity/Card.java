package com.rocketcrew.pocat.domain.card.entity;

import com.rocketcrew.pocat.domain.card.entity.enums.CardCategory;
import com.rocketcrew.pocat.domain.card.entity.enums.CardGrade;
import com.rocketcrew.pocat.domain.card.entity.enums.CardSource;
import com.rocketcrew.pocat.domain.card.entity.enums.CardStatus;
import com.rocketcrew.pocat.global.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Builder
@AllArgsConstructor
@Entity
@Table(name = "cards",
        uniqueConstraints = {
                @UniqueConstraint(columnNames = {"tcgdex_id"})
        },
        indexes = {
                @Index(name = "idx_cards_status", columnList = "status"),
                @Index(name = "idx_cards_user_id", columnList = "user_id"),
                @Index(name = "idx_cards_user_id_status", columnList = "user_id, status"),
                @Index(name = "idx_cards_grade", columnList = "grade"),
                @Index(name = "idx_cards_category", columnList = "category"),
                @Index(name = "idx_cards_created_at", columnList = "created_at")
        }
)
@SQLDelete(sql = "UPDATE cards SET deleted_at = NOW() WHERE id = ?")
@SQLRestriction("deleted_at IS NULL")
public class Card extends BaseEntity {

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "tcgdex_id", length = 100)
    private String tcgdexId;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "series", length = 100, nullable = false)
    private String series;

    @Column(name = "set_id", length = 50, nullable = false)
    private String setId;

    @Column(name = "set_name", length = 100, nullable = false)
    private String setName;

    @Column(name = "card_number", length = 20, nullable = false)
    private String cardNumber;

    @Column(name = "rarity", length = 50, nullable = false)
    private String rarity;

    @Enumerated(EnumType.STRING)
    @Column(name = "category", length = 20, nullable = false)
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
        if (this.status != CardStatus.PENDING) {
            throw new IllegalStateException("PENDING 상태에서만 승인할 수 있습니다.");
        }
        this.status = CardStatus.ACTIVE;
        this.rejectReason = null;
    }

    public void reject(String rejectReason) {
        if (this.status != CardStatus.PENDING) {
            throw new IllegalStateException("PENDING 상태에서만 거절할 수 있습니다.");
        }
        if (rejectReason == null || rejectReason.isBlank()) {
            throw new IllegalArgumentException("거절 사유는 필수입니다.");
        }
        this.status = CardStatus.REJECTED;
        this.rejectReason = rejectReason;
    }

    public void update(String tcgdexId, String name, String series, String setId, String setName,
                       String cardNumber, String rarity, CardCategory category, CardGrade grade,
                       String imageUrl, CardSource source) {
        if (tcgdexId != null) this.tcgdexId = tcgdexId;
        if (name != null && !name.isBlank()) this.name = name;
        if (series != null && !series.isBlank()) this.series = series;
        if (setId != null && !setId.isBlank()) this.setId = setId;
        if (setName != null && !setName.isBlank()) this.setName = setName;
        if (cardNumber != null && !cardNumber.isBlank()) this.cardNumber = cardNumber;
        if (rarity != null && !rarity.isBlank()) this.rarity = rarity;
        if (category != null) this.category = category;
        if (grade != null) this.grade = grade;
        if (imageUrl != null) this.imageUrl = imageUrl;
        if (source != null) this.source = source;
    }
}
