package com.rocketcrew.pocat.domain.card.repository;

import com.rocketcrew.pocat.domain.card.dto.request.CardSearchCondition;
import com.rocketcrew.pocat.domain.card.entity.Card;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;

public interface CardRepositoryCustom {

    Page<Card> searchCards(CardSearchCondition condition, Pageable pageable);

    List<Long> searchCardIds(CardSearchCondition condition);
}
