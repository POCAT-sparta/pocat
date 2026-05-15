package com.rocketcrew.pocat.global.dto;

import org.springframework.http.HttpStatus;

public record SuccessDto<T>(
        boolean success,
        int status,
        T data
) implements ApiResponseDto<T> {

    public SuccessDto(HttpStatus status, T data) {
        this(true, status.value(), data);
    }

    public SuccessDto(HttpStatus status) {
        this(true, status.value(), null);
    }
}
