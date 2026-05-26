package com.rocketcrew.pocat.domain.card.repository;

import com.rocketcrew.pocat.domain.card.document.CardDocument;
import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;

public interface CardSearchRepository extends ElasticsearchRepository<CardDocument, String> {
}
