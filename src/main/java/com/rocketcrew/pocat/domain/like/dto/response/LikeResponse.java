package com.rocketcrew.pocat.domain.like.dto.response;

import java.time.LocalDateTime;

public record LikeResponse(
        Long likeId,
        Long auctionId,
        String auctionTitle,
        String cardName,
        String grade,
        String cardImageUrl,
        Long highestPrice,
        LocalDateTime endedAt,
        String status,
        LocalDateTime createdAt
) {}
