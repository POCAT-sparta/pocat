package com.rocketcrew.pocat.domain.card.dto.request;

import com.rocketcrew.pocat.domain.card.entity.enums.CardCategory;
import com.rocketcrew.pocat.domain.card.entity.enums.CardGrade;
import com.rocketcrew.pocat.domain.card.entity.enums.CardSource;

public record UpdateCardRequest(
        String tcgdexId,
        String name,
        String series,
        String setId,
        String setName,
        String cardNumber,
        String rarity,
        CardCategory category,
        CardGrade grade,
        String imageUrl,
        CardSource source
) {
}
