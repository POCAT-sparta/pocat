package com.rocketcrew.pocat.domain.settlement.repository;

import com.querydsl.core.BooleanBuilder;
import com.querydsl.core.types.Projections;
import com.querydsl.jpa.impl.JPAQueryFactory;
import com.rocketcrew.pocat.domain.card.entity.QCard;
import com.rocketcrew.pocat.domain.order.entity.QOrder;
import com.rocketcrew.pocat.domain.settlement.dto.request.AdminSettlementSearchCondition;
import com.rocketcrew.pocat.domain.settlement.dto.response.AdminSettlementResponse;
import com.rocketcrew.pocat.domain.settlement.entity.QSettlement;
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
public class SettlementRepositoryCustomImpl implements SettlementRepositoryCustom {

    private final JPAQueryFactory queryFactory;

    @Override
    public Page<AdminSettlementResponse> searchSettlements(AdminSettlementSearchCondition condition, Pageable pageable) {
        QSettlement settlement = QSettlement.settlement;
        QUser seller = new QUser("seller");
        QOrder order = QOrder.order;
        QCard card = QCard.card;

        BooleanBuilder where = buildCondition(condition, settlement, seller);

        List<AdminSettlementResponse> content = queryFactory
                .select(Projections.constructor(AdminSettlementResponse.class,
                        settlement.settlementUid,
                        order.orderUid,
                        seller.nickname,
                        card.name,
                        card.grade.stringValue(),
                        settlement.totalPrice,
                        settlement.platformFee,
                        settlement.sellerAmount,
                        settlement.status,
                        settlement.settledAt,
                        settlement.createdAt))
                .from(settlement)
                .leftJoin(seller).on(settlement.sellerId.eq(seller.id))
                .join(order).on(settlement.orderId.eq(order.id))
                .join(card).on(order.cardId.eq(card.id))
                .where(where)
                .orderBy(settlement.createdAt.desc(), settlement.settlementUid.desc())
                .offset(pageable.getOffset())
                .limit(pageable.getPageSize())
                .fetch();

        Long total = queryFactory
                .select(settlement.count())
                .from(settlement)
                .leftJoin(seller).on(settlement.sellerId.eq(seller.id))
                .where(where)
                .fetchOne();

        return new PageImpl<>(content, pageable, total == null ? 0 : total);
    }

    private BooleanBuilder buildCondition(AdminSettlementSearchCondition condition,
                                          QSettlement settlement, QUser seller) {
        BooleanBuilder builder = new BooleanBuilder();

        if (condition.status() != null) {
            builder.and(settlement.status.eq(condition.status()));
        }
        if (StringUtils.hasText(condition.sellerNickname())) {
            builder.and(seller.nickname.containsIgnoreCase(condition.sellerNickname()));
        }
        if (condition.startDate() != null) {
            builder.and(settlement.createdAt.goe(condition.startDate().atStartOfDay()));
        }
        if (condition.endDate() != null) {
            builder.and(settlement.createdAt.lt(condition.endDate().plusDays(1).atStartOfDay()));
        }

        return builder;
    }
}
