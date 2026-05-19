package com.rocketcrew.pocat.domain.user.repository;

import com.querydsl.core.BooleanBuilder;
import com.querydsl.core.types.OrderSpecifier;
import com.querydsl.jpa.impl.JPAQueryFactory;
import com.rocketcrew.pocat.domain.user.entity.QUser;
import com.rocketcrew.pocat.domain.user.entity.User;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.util.StringUtils;

import java.util.List;

@RequiredArgsConstructor
public class UserRepositoryImpl implements UserRepositoryCustom {

    private final JPAQueryFactory queryFactory;

    @Override
    public Page<User> searchUsers(String keyword, Boolean isBidBlocked, Pageable pageable) {
        QUser user = QUser.user;
        BooleanBuilder builder = new BooleanBuilder();

        if (StringUtils.hasText(keyword)) {
            builder.and(
                user.email.containsIgnoreCase(keyword)
                    .or(user.nickname.containsIgnoreCase(keyword))
            );
        }

        if (isBidBlocked != null) {
            builder.and(user.isBidBlocked.eq(isBidBlocked));
        }

        OrderSpecifier<?>[] orders = toOrderSpecifiers(pageable.getSort(), user);

        List<User> content = queryFactory
                .selectFrom(user)
                .where(builder)
                .orderBy(orders)
                .offset(pageable.getOffset())
                .limit(pageable.getPageSize())
                .fetch();

        Long total = queryFactory
                .select(user.count())
                .from(user)
                .where(builder)
                .fetchOne();

        return new PageImpl<>(content, pageable, total == null ? 0 : total);
    }

    private OrderSpecifier<?>[] toOrderSpecifiers(Sort sort, QUser user) {
        if (!sort.isSorted()) {
            return new OrderSpecifier[]{user.createdAt.desc()};
        }
        return sort.stream()
                .map(order -> {
                    boolean desc = order.isDescending();
                    return switch (order.getProperty()) {
                        case "nickname" -> desc ? user.nickname.desc() : user.nickname.asc();
                        case "unpaidStrike" -> desc ? user.unpaidStrike.desc() : user.unpaidStrike.asc();
                        case "email" -> desc ? user.email.desc() : user.email.asc();
                        default -> desc ? user.createdAt.desc() : user.createdAt.asc();
                    };
                })
                .toArray(OrderSpecifier[]::new);
    }
}
