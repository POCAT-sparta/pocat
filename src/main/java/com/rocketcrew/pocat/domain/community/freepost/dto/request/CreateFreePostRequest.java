package com.rocketcrew.pocat.domain.community.freepost.dto.request;

import jakarta.validation.constraints.NotBlank;

public record CreateFreePostRequest(
        @NotBlank(message = "제목을 입력해 주세요.") String title,
        @NotBlank(message = "내용을 입력해 주세요.") String content
) {
}
