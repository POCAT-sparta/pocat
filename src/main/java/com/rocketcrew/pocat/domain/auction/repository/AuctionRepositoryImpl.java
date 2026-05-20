package com.rocketcrew.pocat.domain.auction.repository;

import com.querydsl.core.BooleanBuilder;
import com.querydsl.core.types.Order;
import com.querydsl.core.types.OrderSpecifier;
import com.querydsl.core.types.Projections;
import com.querydsl.core.types.dsl.ComparableExpressionBase;
import com.querydsl.jpa.impl.JPAQueryFactory;
import com.rocketcrew.pocat.domain.auction.dto.request.AuctionSearchCondition;
import com.rocketcrew.pocat.domain.auction.dto.response.SearchAuctionResponse;
import com.rocketcrew.pocat.domain.auction.entity.QAuction;
import com.rocketcrew.pocat.domain.auction.enums.AuctionStatus;
import com.rocketcrew.pocat.domain.card.entity.QCard;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

@Repository
@RequiredArgsConstructor
public class AuctionRepositoryImpl implements AuctionRepositoryCustom {

    private final JPAQueryFactory queryFactory;

    @Override
    public Page<SearchAuctionResponse> searchAuctions(AuctionSearchCondition condition, Pageable pageable) {
        QAuction auction = QAuction.auction;
        QCard card = QCard.card;
        BooleanBuilder where = buildCondition(condition, auction, card);

        return fetchPage(pageable, auction, card, where);
    }

    @Override
    public Page<SearchAuctionResponse> searchMyAuctions(Long sellerId, AuctionStatus status, Pageable pageable) {
        QAuction auction = QAuction.auction;
        QCard card = QCard.card;
        BooleanBuilder where = buildMyCondition(sellerId, status, auction);

        return fetchPage(pageable, auction, card, where);
    }

    private Page<SearchAuctionResponse> fetchPage(Pageable pageable, QAuction auction, QCard card, BooleanBuilder where) {
        List<SearchAuctionResponse> content = queryFactory
                .select(Projections.constructor(SearchAuctionResponse.class,
                        auction.id,
                        auction.title,
                        card.id,
                        card.name,
                        card.grade,
                        auction.cardImageUrl,
                        auction.startingPrice,
                        auction.highestPrice,
                        auction.buyoutPrice,
                        auction.status,
                        auction.startedAt,
                        auction.endedAt,
                        auction.createdAt))
                .from(auction)
                .join(card).on(card.id.eq(auction.cardId))
                .where(where)
                .orderBy(orderSpecifiers(pageable, auction))
                .offset(pageable.getOffset())
                .limit(pageable.getPageSize())
                .fetch();

        Long total = queryFactory
                .select(auction.count())
                .from(auction)
                .join(card).on(card.id.eq(auction.cardId))
                .where(where)
                .fetchOne();

        return new PageImpl<>(content, pageable, total == null ? 0 : total);
    }

    private BooleanBuilder buildCondition(AuctionSearchCondition condition, QAuction auction, QCard card) {
        BooleanBuilder builder = new BooleanBuilder();

        if (condition.status() != null) {
            builder.and(auction.status.eq(condition.status()));
        }

        if (StringUtils.hasText(condition.keyword())) {
            String keyword = condition.keyword();
            builder.and(
                    card.name.containsIgnoreCase(keyword)
                            .or(card.series.containsIgnoreCase(keyword))
                            .or(card.setName.containsIgnoreCase(keyword))
                            .or(card.setId.containsIgnoreCase(keyword))
                            .or(card.cardNumber.containsIgnoreCase(keyword))
                            .or(auction.title.containsIgnoreCase(keyword))
            );
        }
        if (StringUtils.hasText(condition.series())) {
            builder.and(card.series.containsIgnoreCase(condition.series()));
        }
        if (StringUtils.hasText(condition.setName())) {
            builder.and(card.setName.containsIgnoreCase(condition.setName()));
        }
        if (condition.grade() != null) {
            builder.and(card.grade.eq(condition.grade()));
        }
        if (condition.category() != null) {
            builder.and(card.category.eq(condition.category()));
        }

        return builder;
    }

    private BooleanBuilder buildMyCondition(Long sellerId, AuctionStatus status, QAuction auction) {
        BooleanBuilder builder = new BooleanBuilder();

        builder.and(auction.sellerId.eq(sellerId));

        if (status != null) {
            builder.and(auction.status.eq(status));
        }

        return builder;
    }

    private OrderSpecifier<?>[] orderSpecifiers(Pageable pageable, QAuction auction) {
        if (pageable.getSort().isUnsorted()) {
            return new OrderSpecifier<?>[] {
                    auction.startedAt.desc(),
                    auction.id.desc()
            };
        }

        List<OrderSpecifier<?>> orders = new ArrayList<>();
        for (Sort.Order sort : pageable.getSort()) {
            orders.add(toOrderSpecifier(sort, auction));
        }
        if (!hasIdSort(pageable.getSort())) {
            orders.add(auction.id.desc());
        }

        return orders.toArray(OrderSpecifier[]::new);
    }

    private boolean hasIdSort(Sort sort) {
        return sort.stream()
                .anyMatch(order -> "id".equals(order.getProperty()));
    }

    private OrderSpecifier<?> toOrderSpecifier(Sort.Order sort, QAuction auction) {
        Order direction = sort.isAscending() ? Order.ASC : Order.DESC;
        ComparableExpressionBase<?> target = switch (sort.getProperty()) {
            case "endedAt" -> auction.endedAt;
            case "startedAt" -> auction.startedAt;
            case "highestPrice" -> auction.highestPrice;
            case "startingPrice" -> auction.startingPrice;
            case "createdAt" -> auction.createdAt;
            case "id" -> auction.id;
            default -> auction.createdAt;
        };
        return new OrderSpecifier<>(direction, target);
    }
}
