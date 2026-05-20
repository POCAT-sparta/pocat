package com.rocketcrew.pocat.domain.card.dto.request;

import com.rocketcrew.pocat.domain.card.entity.enums.CardGrade;
import com.rocketcrew.pocat.domain.card.entity.enums.CardSource;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CreateCardRequest(
        String tcgdexId,
        @NotBlank String name,
        String series,
        String setName,
        String cardNumber,
        String rarity,
        @NotNull CardGrade grade,
        String imageUrl,
        @NotNull CardSource source
) {
}
