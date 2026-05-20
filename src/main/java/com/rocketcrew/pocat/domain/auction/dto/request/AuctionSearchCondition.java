package com.rocketcrew.pocat.domain.auction.dto.request;

import com.rocketcrew.pocat.domain.auction.enums.AuctionStatus;
import com.rocketcrew.pocat.domain.card.entity.enums.CardCategory;
import com.rocketcrew.pocat.domain.card.entity.enums.CardGrade;

public record AuctionSearchCondition(
        String keyword,
        String series,
        String setName,
        CardGrade grade,
        CardCategory category,
        AuctionStatus status
) {
}
