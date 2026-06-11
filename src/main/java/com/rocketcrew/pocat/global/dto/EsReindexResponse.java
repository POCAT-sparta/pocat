package com.rocketcrew.pocat.global.dto;

public record EsReindexResponse(
        String previousIndex,
        String newIndex,
        long documentCount,
        String message
) {
}
