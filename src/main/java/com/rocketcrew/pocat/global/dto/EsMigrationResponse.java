package com.rocketcrew.pocat.global.dto;

public record EsMigrationResponse(
        int cardsMigrated,
        int auctionsMigrated
) {
}
