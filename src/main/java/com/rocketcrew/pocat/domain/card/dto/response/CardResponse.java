package com.rocketcrew.pocat.domain.card.dto.response;

import com.rocketcrew.pocat.domain.card.entity.Card;
import com.rocketcrew.pocat.domain.card.entity.enums.CardCategory;
import com.rocketcrew.pocat.domain.card.entity.enums.CardGrade;
import com.rocketcrew.pocat.domain.card.entity.enums.CardSource;
import com.rocketcrew.pocat.domain.card.entity.enums.CardStatus;
import com.rocketcrew.pocat.domain.series.entity.Series;
import com.rocketcrew.pocat.domain.set.entity.PokemonSet;

import java.time.LocalDateTime;

public record CardResponse(
        Long id,
        Long userId,
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
        CardSource source,
        CardStatus status,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public static CardResponse from(Card card) {
        Series series = card.getSeries();
        PokemonSet pokemonSet = card.getPokemonSet();
        return new CardResponse(
                card.getId(),
                card.getUserId(),
                card.getTcgdexId(),
                card.getName(),
                series != null ? series.getName() : null,
                pokemonSet != null ? pokemonSet.getSetId() : null,
                pokemonSet != null ? pokemonSet.getName() : null,
                card.getCardNumber(),
                card.getRarity(),
                card.getCategory(),
                card.getGrade(),
                card.getImageUrl(),
                card.getSource(),
                card.getStatus(),
                card.getCreatedAt(),
                card.getUpdatedAt()
        );
    }
}
