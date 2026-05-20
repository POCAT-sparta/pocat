package com.rocketcrew.pocat.domain.refund.repository;

import com.querydsl.core.BooleanBuilder;
import com.querydsl.core.types.Projections;
import com.querydsl.jpa.impl.JPAQueryFactory;
import com.rocketcrew.pocat.domain.order.entity.QOrder;
import com.rocketcrew.pocat.domain.refund.dto.response.AdminRefundResponse;
import com.rocketcrew.pocat.domain.refund.dto.response.RefundResponse;
import com.rocketcrew.pocat.domain.refund.entity.QRefund;
import com.rocketcrew.pocat.domain.refund.entity.RefundStatus;
import com.rocketcrew.pocat.domain.user.entity.QUser;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
@RequiredArgsConstructor
public class RefundRepositoryCustomImpl implements RefundRepositoryCustom {

    private final JPAQueryFactory queryFactory;

    @Override
    public Page<RefundResponse> findMyRefunds(Long buyerId, RefundStatus status, Pageable pageable) {
        QRefund refund = QRefund.refund;
        QOrder order = QOrder.order;

        BooleanBuilder where = new BooleanBuilder();
        where.and(order.buyerId.eq(buyerId));
        if (status != null) {
            where.and(refund.status.eq(status));
        }

        List<RefundResponse> content = queryFactory
                .select(Projections.constructor(RefundResponse.class,
                        refund.id,
                        refund.orderId,
                        refund.paymentId,
                        refund.amount,
                        refund.reason,
                        refund.rejectReason,
                        refund.status,
                        refund.createdAt,
                        refund.updatedAt))
                .from(refund)
                .join(order).on(refund.orderId.eq(order.id))
                .where(where)
                .orderBy(refund.createdAt.desc())
                .offset(pageable.getOffset())
                .limit(pageable.getPageSize())
                .fetch();

        Long total = queryFactory
                .select(refund.count())
                .from(refund)
                .join(order).on(refund.orderId.eq(order.id))
                .where(where)
                .fetchOne();

        return new PageImpl<>(content, pageable, total == null ? 0 : total);
    }

    @Override
    public Page<AdminRefundResponse> findAdminRefunds(RefundStatus status, Pageable pageable) {
        QRefund refund = QRefund.refund;
        QOrder order = QOrder.order;
        QUser buyer = new QUser("buyer");

        BooleanBuilder where = new BooleanBuilder();
        if (status != null) {
            where.and(refund.status.eq(status));
        }

        List<AdminRefundResponse> content = queryFactory
                .select(Projections.constructor(AdminRefundResponse.class,
                        refund.id,
                        refund.orderId,
                        buyer.nickname,
                        refund.amount,
                        refund.reason,
                        refund.status,
                        refund.createdAt,
                        refund.updatedAt))
                .from(refund)
                .join(order).on(refund.orderId.eq(order.id))
                .leftJoin(buyer).on(order.buyerId.eq(buyer.id))
                .where(where)
                .orderBy(refund.createdAt.desc())
                .offset(pageable.getOffset())
                .limit(pageable.getPageSize())
                .fetch();

        Long total = queryFactory
                .select(refund.count())
                .from(refund)
                .join(order).on(refund.orderId.eq(order.id))
                .where(where)
                .fetchOne();

        return new PageImpl<>(content, pageable, total == null ? 0 : total);
    }
}
