package com.rocketcrew.pocat.domain.order.dto.response;

import com.rocketcrew.pocat.domain.card.entity.Card;
import com.rocketcrew.pocat.domain.order.entity.Order;
import com.rocketcrew.pocat.domain.user.entity.User;

import java.time.LocalDateTime;

public record OrderDetailResponse(
        String orderUid,
        Long auctionId,
        UserInfo buyer,
        UserInfo seller,
        CardInfo card,
        Long finalPrice,
        String orderStatus,
        String cancelReason,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public record UserInfo(String nickname) {}

    public record CardInfo(String name, String grade, String imageUrl) {}

    public static OrderDetailResponse of(Order order, User buyer, User seller, Card card) {
        return new OrderDetailResponse(
                order.getOrderUid(),
                order.getAuctionId(),
                new UserInfo(buyer.getNickname()),
                new UserInfo(seller.getNickname()),
                new CardInfo(card.getName(), card.getGrade().name(), card.getImageUrl()),
                order.getFinalPrice(),
                order.getStatus().name(),
                order.getCancelReason(),
                order.getCreatedAt(),
                order.getUpdatedAt()
        );
    }
}
