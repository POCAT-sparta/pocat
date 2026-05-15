package com.rocketcrew.pocat.global.dto;

/**
 * @param data 에러 상세 데이터 (보통 null)
 */
public record ErrorDto<T>(
        String code,
        String message,
        T data
)
        implements ApiResponseDto<T>
{}