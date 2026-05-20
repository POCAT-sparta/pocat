package com.rocketcrew.pocat.domain.like.dto.request;

public record ToggleLikeRequest(Long auctionId) {
    public ToggleLikeRequest {
        if (auctionId == null) {
            throw new IllegalArgumentException("auctionId must not be null");
        }
    }
}
