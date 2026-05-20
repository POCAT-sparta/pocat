package com.rocketcrew.pocat.domain.community.freepost.repository;

import com.querydsl.core.BooleanBuilder;
import com.querydsl.core.types.OrderSpecifier;
import com.querydsl.jpa.impl.JPAQueryFactory;
import com.rocketcrew.pocat.domain.community.freepost.entity.FreePost;
import com.rocketcrew.pocat.domain.community.freepost.entity.QFreePost;
import com.rocketcrew.pocat.global.exception.common.ErrorCode;
import com.rocketcrew.pocat.global.exception.domain.FreePostException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.util.StringUtils;

import java.util.List;

@RequiredArgsConstructor
public class FreePostRepositoryImpl implements FreePostRepositoryCustom {

    private final JPAQueryFactory queryFactory;

    @Override
    public Page<FreePost> searchPosts(String keyword, Pageable pageable) {
        QFreePost freePost = QFreePost.freePost;
        BooleanBuilder builder = new BooleanBuilder();

        if (StringUtils.hasText(keyword)) {
            builder.and(
                freePost.title.containsIgnoreCase(keyword)
                    .or(freePost.content.containsIgnoreCase(keyword))
            );
        }

        OrderSpecifier<?> order = resolveOrder(freePost, pageable.getSort());

        List<FreePost> content = queryFactory
                .selectFrom(freePost)
                .where(builder)
                .orderBy(order)
                .offset(pageable.getOffset())
                .limit(pageable.getPageSize())
                .fetch();

        Long total = queryFactory
                .select(freePost.count())
                .from(freePost)
                .where(builder)
                .fetchOne();

        return new PageImpl<>(content, pageable, total == null ? 0 : total);
    }

    private OrderSpecifier<?> resolveOrder(QFreePost freePost, Sort sort) {
        if (sort.isSorted()) {
            Sort.Order order = sort.iterator().next();
            boolean desc = order.isDescending();
            return switch (order.getProperty()) {
                case "viewCount" -> desc ? freePost.viewCount.desc() : freePost.viewCount.asc();
                case "createdAt" -> desc ? freePost.createdAt.desc() : freePost.createdAt.asc();
                default -> throw new FreePostException(ErrorCode.INVALID_INPUT);
            };
        }
        return freePost.createdAt.desc();
    }
}
