package com.rocketcrew.pocat.domain.card.dto.request;

import com.rocketcrew.pocat.domain.card.entity.CardGrade;
import com.rocketcrew.pocat.domain.card.entity.CardSource;

public record CreateCardRequest(
        String tcgdexId,
        String name,
        String series,
        String setName,
        String cardNumber,
        String rarity,
        CardGrade grade,
        String imageUrl,
        CardSource source
) {
}
