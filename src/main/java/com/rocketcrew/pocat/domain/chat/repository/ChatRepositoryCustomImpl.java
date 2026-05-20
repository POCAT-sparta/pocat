package com.rocketcrew.pocat.domain.chat.repository;

import com.querydsl.core.types.Projections;
import com.querydsl.core.types.dsl.CaseBuilder;
import com.querydsl.jpa.JPAExpressions;
import com.querydsl.jpa.impl.JPAQueryFactory;
import com.rocketcrew.pocat.domain.chat.dto.response.ChatRoomListResponse;
import com.rocketcrew.pocat.domain.chat.entity.QChat;
import com.rocketcrew.pocat.domain.chat.entity.QChatMessage;
import com.rocketcrew.pocat.domain.community.tradepost.entity.QTradePost;
import com.rocketcrew.pocat.domain.user.entity.QUser;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
@RequiredArgsConstructor
public class ChatRepositoryCustomImpl implements ChatRepositoryCustom {

    private final JPAQueryFactory queryFactory;

    @Override
    public List<ChatRoomListResponse> findMyChatsWithDetails(Long userId) {
        QChat chat = QChat.chat;
        QTradePost post = QTradePost.tradePost;
        QUser owner = new QUser("owner");
        QUser guest = new QUser("guest");
        QChatMessage lastMsg = QChatMessage.chatMessage;
        QChatMessage subMsg = new QChatMessage("subMsg");

        return queryFactory
                .select(Projections.constructor(ChatRoomListResponse.class,
                        chat.id,
                        post.title,
                        new CaseBuilder()
                                .when(chat.ownerId.eq(userId)).then(guest.nickname)
                                .otherwise(owner.nickname),
                        lastMsg.message,
                        chat.status,
                        chat.updatedAt
                ))
                .from(chat)
                .join(post).on(post.id.eq(chat.postId))
                .join(owner).on(owner.id.eq(chat.ownerId))
                .join(guest).on(guest.id.eq(chat.guestId))
                .leftJoin(lastMsg).on(
                        lastMsg.chatId.eq(chat.id).and(
                                lastMsg.id.eq(
                                        JPAExpressions.select(subMsg.id.max())
                                                .from(subMsg)
                                                .where(subMsg.chatId.eq(chat.id))
                                )
                        )
                )
                .where(
                        chat.ownerId.eq(userId).and(chat.ownerLeft.isFalse())
                                .or(chat.guestId.eq(userId).and(chat.guestLeft.isFalse()))
                )
                .orderBy(chat.updatedAt.desc())
                .fetch();
    }
}
