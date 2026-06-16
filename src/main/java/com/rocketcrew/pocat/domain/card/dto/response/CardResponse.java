package com.rocketcrew.pocat.domain.card.dto.response;

import com.rocketcrew.pocat.domain.card.entity.Card;
import com.rocketcrew.pocat.domain.card.entity.enums.CardCategory;
import com.rocketcrew.pocat.domain.card.entity.enums.CardGrade;
import com.rocketcrew.pocat.domain.card.entity.enums.CardSource;
import com.rocketcrew.pocat.domain.card.entity.enums.CardStatus;
import com.rocketcrew.pocat.domain.pokemon.entity.Pokemon;
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
        LocalDateTime updatedAt,
        String cardDetailName,    // 상세 화면 표시용 이름. 포켓몬 연결 시 pokemon.name, 없으면 card.name. 항상 값 있음
        String cardDetailNameKo,  // 상세 화면 표시용 한글 이름. 포켓몬 연결 + nameKo 있을 때만 값, 그 외엔 null
        ActiveAuctionSummary activeAuction,  // 단건 조회용
        int activeAuctionCount               // 목록 조회용
) {
    public static CardResponse from(Card card) {
        return from(card, null);
    }

    public static CardResponse from(Card card, ActiveAuctionSummary activeAuction) {
        Series series = card.getSeries();
        PokemonSet pokemonSet = card.getPokemonSet();
        Pokemon pokemon = card.getPokemon();
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
                card.getUpdatedAt(),
                pokemon != null ? pokemon.getName() : card.getName(),
                pokemon != null ? pokemon.getNameKo() : null,
                activeAuction,
                0
        );
    }

    /** 단건 조회: 진행 중인 경매 상세 정보 붙이기 */
    public CardResponse withActiveAuction(ActiveAuctionSummary activeAuction) {
        return new CardResponse(id, userId, tcgdexId, name, series, setId, setName,
                cardNumber, rarity, category, grade, imageUrl, source, status,
                createdAt, updatedAt, cardDetailName, cardDetailNameKo, activeAuction, 0);
    }

    /** 목록 조회: 진행 중인 경매 건수 붙이기 */
    public CardResponse withActiveAuctionCount(int count) {
        return new CardResponse(id, userId, tcgdexId, name, series, setId, setName,
                cardNumber, rarity, category, grade, imageUrl, source, status,
                createdAt, updatedAt, cardDetailName, cardDetailNameKo, null, count);
    }

}
