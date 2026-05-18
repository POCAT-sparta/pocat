package com.rocketcrew.pocat.domain.community.freepost.dto.request;

public record CreateFreePostRequest(
        String title,
        String content
) {
}
