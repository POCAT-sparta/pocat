package com.rocketcrew.pocat.domain.order.repository;

import com.querydsl.core.BooleanBuilder;
import com.querydsl.core.types.Projections;
import com.querydsl.jpa.impl.JPAQueryFactory;
import com.rocketcrew.pocat.domain.card.entity.QCard;
import com.rocketcrew.pocat.domain.order.dto.request.AdminOrderSearchCondition;
import com.rocketcrew.pocat.domain.order.dto.response.AdminOrderResponse;
import com.rocketcrew.pocat.domain.order.entity.QOrder;
import com.rocketcrew.pocat.domain.user.entity.QUser;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

import java.util.List;

@Repository
@RequiredArgsConstructor
public class OrderRepositoryCustomImpl implements OrderRepositoryCustom {

    private final JPAQueryFactory queryFactory;

    @Override
    public Page<AdminOrderResponse> searchOrders(AdminOrderSearchCondition condition, Pageable pageable) {
        QOrder order = QOrder.order;
        QUser buyer = new QUser("buyer");
        QUser seller = new QUser("seller");
        QCard card = QCard.card;

        BooleanBuilder where = buildCondition(condition, order, buyer, seller, card);

        List<AdminOrderResponse> content = queryFactory
                .select(Projections.constructor(AdminOrderResponse.class,
                        order.id,
                        order.orderUid,
                        buyer.nickname,
                        seller.nickname,
                        card.name,
                        card.grade.stringValue(),
                        order.finalPrice,
                        order.status,
                        order.deliveryStatus,
                        order.createdAt))
                .from(order)
                .leftJoin(buyer).on(order.buyerId.eq(buyer.id))
                .leftJoin(seller).on(order.sellerId.eq(seller.id))
                .join(card).on(order.cardId.eq(card.id))
                .where(where)
                .offset(pageable.getOffset())
                .limit(pageable.getPageSize())
                .fetch();

        Long total = queryFactory
                .select(order.count())
                .from(order)
                .leftJoin(buyer).on(order.buyerId.eq(buyer.id))
                .leftJoin(seller).on(order.sellerId.eq(seller.id))
                .join(card).on(order.cardId.eq(card.id))
                .where(where)
                .fetchOne();

        return new PageImpl<>(content, pageable, total == null ? 0 : total);
    }

    private BooleanBuilder buildCondition(AdminOrderSearchCondition condition,
                                          QOrder order, QUser buyer, QUser seller, QCard card) {
        BooleanBuilder builder = new BooleanBuilder();

        if (condition.orderStatus() != null) {
            builder.and(order.status.eq(condition.orderStatus()));
        }
        if (condition.deliveryStatus() != null) {
            builder.and(order.deliveryStatus.eq(condition.deliveryStatus()));
        }
        if (condition.cardGrade() != null) {
            builder.and(card.grade.eq(condition.cardGrade()));
        }
        if (condition.startDate() != null) {
            builder.and(order.createdAt.goe(condition.startDate()));
        }
        if (condition.endDate() != null) {
            builder.and(order.createdAt.loe(condition.endDate()));
        }
        if (StringUtils.hasText(condition.buyerNickname())) {
            builder.and(buyer.nickname.containsIgnoreCase(condition.buyerNickname()));
        }
        if (StringUtils.hasText(condition.sellerNickname())) {
            builder.and(seller.nickname.containsIgnoreCase(condition.sellerNickname()));
        }

        return builder;
    }
}
