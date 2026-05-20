package com.rocketcrew.pocat.domain.card.repository;

import com.querydsl.core.BooleanBuilder;
import com.querydsl.jpa.impl.JPAQueryFactory;
import com.rocketcrew.pocat.domain.card.dto.request.CardSearchCondition;
import com.rocketcrew.pocat.domain.card.entity.Card;
import com.rocketcrew.pocat.domain.card.entity.QCard;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.util.StringUtils;

import java.util.List;

@RequiredArgsConstructor
public class CardRepositoryImpl implements CardRepositoryCustom {

    private final JPAQueryFactory queryFactory;

    @Override
    public Page<Card> searchCards(CardSearchCondition condition, Pageable pageable) {
        QCard card = QCard.card;
        BooleanBuilder builder = buildCondition(condition);

        List<Card> content = queryFactory
                .selectFrom(card)
                .where(builder)
                .orderBy(card.createdAt.desc(), card.id.desc())
                .offset(pageable.getOffset())
                .limit(pageable.getPageSize())
                .fetch();

        Long total = queryFactory
                .select(card.count())
                .from(card)
                .where(builder)
                .fetchOne();

        return new PageImpl<>(content, pageable, total == null ? 0 : total);
    }

    @Override
    public List<Long> searchCardIds(CardSearchCondition condition) {
        QCard card = QCard.card;

        return queryFactory
                .select(card.id)
                .from(card)
                .where(buildCondition(condition))
                .fetch();
    }

    private BooleanBuilder buildCondition(CardSearchCondition condition) {
        QCard card = QCard.card;
        BooleanBuilder builder = new BooleanBuilder();

        if (StringUtils.hasText(condition.keyword())) {
            String kw = condition.keyword();
            builder.and(
                card.name.containsIgnoreCase(kw)
                    .or(card.series.containsIgnoreCase(kw))
                    .or(card.setName.containsIgnoreCase(kw))
            );
        }
        if (condition.grade() != null) {
            builder.and(card.grade.eq(condition.grade()));
        }
        if (condition.category() != null) {
            builder.and(card.category.eq(condition.category()));
        }
        if (condition.status() != null) {
            builder.and(card.status.eq(condition.status()));
        }

        return builder;
    }
}
