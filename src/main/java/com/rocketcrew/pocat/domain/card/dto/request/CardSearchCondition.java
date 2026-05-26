package com.rocketcrew.pocat.domain.card.dto.request;

import com.rocketcrew.pocat.domain.card.entity.enums.CardCategory;
import com.rocketcrew.pocat.domain.card.entity.enums.CardGrade;
import com.rocketcrew.pocat.domain.card.entity.enums.CardStatus;

public record CardSearchCondition(
        String keyword,
        String series,
        String setName,
        String rarity,
        CardGrade grade,
        CardCategory category,
        CardStatus status
) {
}
