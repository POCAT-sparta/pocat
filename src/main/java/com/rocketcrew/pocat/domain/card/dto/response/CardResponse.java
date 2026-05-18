package com.rocketcrew.pocat.domain.card.dto.response;

import com.rocketcrew.pocat.domain.card.entity.Card;
import com.rocketcrew.pocat.domain.card.entity.enums.CardGrade;
import com.rocketcrew.pocat.domain.card.entity.enums.CardSource;
import com.rocketcrew.pocat.domain.card.entity.enums.CardStatus;

import java.time.LocalDateTime;

public record CardResponse(
        Long id,
        Long userId,
        String tcgdexId,
        String name,
        String series,
        String setName,
        String cardNumber,
        String rarity,
        CardGrade grade,
        String imageUrl,
        CardSource source,
        CardStatus status,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public static CardResponse from(Card card) {
        return new CardResponse(
                card.getId(),
                card.getUserId(),
                card.getTcgdexId(),
                card.getName(),
                card.getSeries(),
                card.getSetName(),
                card.getCardNumber(),
                card.getRarity(),
                card.getGrade(),
                card.getImageUrl(),
                card.getSource(),
                card.getStatus(),
                card.getCreatedAt(),
                card.getUpdatedAt()
        );
    }
}
