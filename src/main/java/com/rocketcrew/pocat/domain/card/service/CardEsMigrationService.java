package com.rocketcrew.pocat.domain.card.service;

import com.rocketcrew.pocat.domain.card.document.CardDocument;
import com.rocketcrew.pocat.domain.card.entity.Card;
import com.rocketcrew.pocat.domain.card.entity.enums.CardStatus;
import com.rocketcrew.pocat.domain.card.repository.CardRepository;
import com.rocketcrew.pocat.domain.card.repository.CardSearchRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class CardEsMigrationService {

    private static final int BATCH_SIZE = 500;

    private final CardRepository cardRepository;
    private final CardSearchRepository cardSearchRepository;

    @Transactional(readOnly = true)
    public int migrateAll() {
        int pageNum = 0;
        int totalCount = 0;
        Page<Card> batch;

        do {
            Pageable pageable = PageRequest.of(pageNum++, BATCH_SIZE);
            batch = cardRepository.findByStatus(CardStatus.ACTIVE, pageable);

            List<CardDocument> docs = batch.getContent().stream()
                    .map(card -> CardDocument.from(
                            card,
                            card.getPokemon() != null ? card.getPokemon().getNameKo() : null,
                            card.getSeries() != null ? card.getSeries().getNameKo() : null,
                            card.getPokemonSet() != null ? card.getPokemonSet().getNameKo() : null
                    ))
                    .collect(java.util.stream.Collectors.toList());

            if (!docs.isEmpty()) {
                cardSearchRepository.saveAll(docs);
                totalCount += docs.size();
                log.info("[ES_MIGRATION] 카드 배치 인덱싱 완료 누적={} 전체={}", totalCount, batch.getTotalElements());
            }
        } while (batch.hasNext());

        log.info("[ES_MIGRATION] 카드 전체 ES 인덱싱 완료 count={}", totalCount);
        return totalCount;
    }
}
