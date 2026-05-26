package com.rocketcrew.pocat.domain.card.service;

import com.rocketcrew.pocat.domain.card.document.CardDocument;
import com.rocketcrew.pocat.domain.card.entity.Card;
import com.rocketcrew.pocat.domain.card.entity.enums.CardStatus;
import com.rocketcrew.pocat.domain.card.repository.CardRepository;
import com.rocketcrew.pocat.domain.card.repository.CardSearchRepository;
import com.rocketcrew.pocat.domain.card.util.PokemonNameDictionary;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class CardEsMigrationService {

    private final CardRepository cardRepository;
    private final CardSearchRepository cardSearchRepository;
    private final PokemonNameDictionary pokemonNameDictionary;

    @Transactional(readOnly = true)
    public int migrateAll() {
        List<Card> activeCards = cardRepository.findAllByStatus(CardStatus.ACTIVE);
        List<CardDocument> docs = activeCards.stream()
                .map(card -> CardDocument.from(card, pokemonNameDictionary.findKoreanName(card.getName())))
                .toList();
        cardSearchRepository.saveAll(docs);
        log.info("[EsMigration] {}개 카드 ES 인덱싱 완료", docs.size());
        return docs.size();
    }
}
