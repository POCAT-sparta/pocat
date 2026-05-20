package com.rocketcrew.pocat.domain.community.tradepost.repository;

import com.querydsl.core.BooleanBuilder;
import com.querydsl.jpa.impl.JPAQueryFactory;
import com.rocketcrew.pocat.domain.community.tradepost.entity.QTradePost;
import com.rocketcrew.pocat.domain.community.tradepost.entity.TradePost;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.util.List;

@RequiredArgsConstructor
public class TradePostRepositoryImpl implements TradePostRepositoryCustom {

    private final JPAQueryFactory queryFactory;

    @Override
    public Page<TradePost> searchPosts(String keyword, Long minPrice, Long maxPrice, Pageable pageable) {
        QTradePost tradePost = QTradePost.tradePost;
        BooleanBuilder builder = new BooleanBuilder();

        if (keyword != null && !keyword.isBlank()) {
            builder.and(tradePost.title.containsIgnoreCase(keyword)
                    .or(tradePost.content.containsIgnoreCase(keyword)));
        }
        if (minPrice != null) {
            builder.and(tradePost.price.goe(minPrice));
        }
        if (maxPrice != null) {
            builder.and(tradePost.price.loe(maxPrice));
        }

        List<TradePost> content = queryFactory
                .selectFrom(tradePost)
                .where(builder)
                .offset(pageable.getOffset())
                .limit(pageable.getPageSize())
                .orderBy(tradePost.createdAt.desc())
                .fetch();

        Long total = queryFactory
                .select(tradePost.count())
                .from(tradePost)
                .where(builder)
                .fetchOne();

        return new PageImpl<>(content, pageable, total == null ? 0 : total);
    }
}
