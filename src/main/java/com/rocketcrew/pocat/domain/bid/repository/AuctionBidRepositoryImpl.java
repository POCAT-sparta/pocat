package com.rocketcrew.pocat.domain.bid.repository;

import com.querydsl.core.BooleanBuilder;
import com.querydsl.core.types.OrderSpecifier;
import com.querydsl.core.types.Projections;
import com.querydsl.jpa.impl.JPAQueryFactory;
import com.rocketcrew.pocat.domain.auction.entity.QAuction;
import com.rocketcrew.pocat.domain.bid.dto.response.AuctionBidHistoryResponse;
import com.rocketcrew.pocat.domain.bid.dto.response.MyBidResponse;
import com.rocketcrew.pocat.domain.bid.entity.QAuctionBid;
import com.rocketcrew.pocat.domain.bid.enums.BidStatus;
import com.rocketcrew.pocat.domain.user.entity.QUser;
import com.rocketcrew.pocat.global.exception.common.ErrorCode;
import com.rocketcrew.pocat.global.exception.domain.BidException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.util.ArrayList;
import java.util.List;

@RequiredArgsConstructor
public class AuctionBidRepositoryImpl implements AuctionBidRepositoryCustom {

    private final JPAQueryFactory queryFactory;

    @Override
    public Page<MyBidResponse> findMyBids(Long userId, BidStatus status, Pageable pageable) {
        QAuctionBid bid = QAuctionBid.auctionBid;
        QAuction auction = QAuction.auction;
        BooleanBuilder where = myBidsCondition(userId, status, bid);

        List<MyBidResponse> content = queryFactory
                .select(Projections.constructor(MyBidResponse.class,
                        bid.id,
                        auction.id,
                        auction.title,
                        bid.bidPrice,
                        bid.status,
                        bid.createdAt))
                .from(bid)
                .join(auction).on(auction.id.eq(bid.auctionId))
                .where(where)
                .orderBy(myBidsOrderSpecifiers(pageable.getSort(), bid))
                .offset(pageable.getOffset())
                .limit(pageable.getPageSize())
                .fetch();

        Long total = queryFactory
                .select(bid.count())
                .from(bid)
                .join(auction).on(auction.id.eq(bid.auctionId))
                .where(where)
                .fetchOne();

        return new PageImpl<>(content, pageable, total == null ? 0 : total);
    }

    @Override
    public Page<AuctionBidHistoryResponse> findBidHistoryByAuctionId(Long auctionId, Pageable pageable) {
        QAuctionBid bid = QAuctionBid.auctionBid;
        QUser user = QUser.user;

        List<AuctionBidHistoryResponse> content = queryFactory
                .select(Projections.constructor(AuctionBidHistoryResponse.class,
                        bid.id,
                        user.id,
                        user.nickname,
                        bid.bidPrice,
                        bid.createdAt))
                .from(bid)
                .join(user).on(user.id.eq(bid.userId))
                .where(bid.auctionId.eq(auctionId))
                .orderBy(bidHistoryOrderSpecifiers(pageable.getSort(), bid))
                .offset(pageable.getOffset())
                .limit(pageable.getPageSize())
                .fetch();

        Long total = queryFactory
                .select(bid.count())
                .from(bid)
                .join(user).on(user.id.eq(bid.userId))
                .where(bid.auctionId.eq(auctionId))
                .fetchOne();

        return new PageImpl<>(content, pageable, total == null ? 0 : total);
    }

    @Override
    public List<Long> findDistinctBidderIdsByAuctionId(Long auctionId) {
        QAuctionBid bid = QAuctionBid.auctionBid;
        return queryFactory
                .select(bid.userId)
                .distinct()
                .from(bid)
                .where(bid.auctionId.eq(auctionId))
                .fetch();
    }

    private BooleanBuilder myBidsCondition(Long userId, BidStatus status, QAuctionBid bid) {
        BooleanBuilder builder = new BooleanBuilder();
        builder.and(bid.userId.eq(userId));

        if (status != null) {
            builder.and(bid.status.eq(status));
        }

        return builder;
    }

    private OrderSpecifier<?>[] myBidsOrderSpecifiers(Sort sort, QAuctionBid bid) {
        if (sort.isUnsorted()) {
            return new OrderSpecifier<?>[] {
                    bid.createdAt.desc(),
                    bid.id.desc()
            };
        }

        List<OrderSpecifier<?>> orders = new ArrayList<>();
        for (Sort.Order order : sort) {
            boolean desc = order.isDescending();
            OrderSpecifier<?> orderSpecifier = switch (order.getProperty()) {
                case "bidPrice" -> desc ? bid.bidPrice.desc() : bid.bidPrice.asc();
                case "status" -> desc ? bid.status.desc() : bid.status.asc();
                case "id", "bidId" -> desc ? bid.id.desc() : bid.id.asc();
                case "createdAt" -> desc ? bid.createdAt.desc() : bid.createdAt.asc();
                default -> throw new BidException(ErrorCode.INVALID_INPUT);
            };
            orders.add(orderSpecifier);
        }

        if (!hasIdSort(sort)) {
            orders.add(bid.id.desc());
        }

        return orders.toArray(OrderSpecifier[]::new);
    }

    private OrderSpecifier<?>[] bidHistoryOrderSpecifiers(Sort sort, QAuctionBid bid) {
        if (sort.isUnsorted()) {
            return new OrderSpecifier<?>[] {
                    bid.bidPrice.desc(),
                    bid.createdAt.desc(),
                    bid.id.desc()
            };
        }

        List<OrderSpecifier<?>> orders = new ArrayList<>();
        for (Sort.Order order : sort) {
            boolean desc = order.isDescending();
            OrderSpecifier<?> orderSpecifier = switch (order.getProperty()) {
                case "bidPrice" -> desc ? bid.bidPrice.desc() : bid.bidPrice.asc();
                case "createdAt" -> desc ? bid.createdAt.desc() : bid.createdAt.asc();
                case "id", "bidId" -> desc ? bid.id.desc() : bid.id.asc();
                default -> throw new BidException(ErrorCode.INVALID_INPUT);
            };
            orders.add(orderSpecifier);
        }

        if (!hasIdSort(sort)) {
            orders.add(bid.id.desc());
        }

        return orders.toArray(OrderSpecifier[]::new);
    }

    private boolean hasIdSort(Sort sort) {
        return sort.stream()
                .anyMatch(order -> "id".equals(order.getProperty()) || "bidId".equals(order.getProperty()));
    }
}
