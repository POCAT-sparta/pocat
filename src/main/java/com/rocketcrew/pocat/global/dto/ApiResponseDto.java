package com.rocketcrew.pocat.global.dto;


import org.springframework.http.HttpStatus;

public interface ApiResponseDto<T> {

    static <T> ApiResponseDto<T> success(HttpStatus status, T data) {
        return new SuccessDto<>(status, data);
    }
    static <T> ApiResponseDto<T> successWithNoContent() {
        return new SuccessDto<>(HttpStatus.NO_CONTENT);
    }

    static <T> ApiResponseDto<T> error(String code, String message) {
        return new ErrorDto<>(code, message, null);
    }

    static <T> ApiResponseDto<T> errorWithMap(T map, String code, String message) {
        return new ErrorDto<>(code, message, map);
    }
}
