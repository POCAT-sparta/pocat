package com.rocketcrew.pocat.global.exception.common;

import com.rocketcrew.pocat.global.dto.ApiResponseDto;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.MalformedJwtException;
import io.jsonwebtoken.security.SignatureException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    // 409 - 데이터 무결성 위반 (중복 데이터 등)
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiResponseDto<Void>> handleDataIntegrityViolationException(DataIntegrityViolationException ex) {
        return ResponseEntity
                .status(HttpStatus.CONFLICT)
                .body(ApiResponseDto.error(
                        "DATA_INTEGRITY_VIOLATION",
                        "이미 존재하는 데이터이거나 데이터 제약 조건을 위반했습니다."
                ));
    }

    // 도메인 커스텀 예외
    @ExceptionHandler(ServiceException.class)
    public ResponseEntity<ApiResponseDto<Void>> handleServiceException(ServiceException ex) {
        return ResponseEntity
                .status(ex.getStatus())
                .body(ApiResponseDto.error(
                        ex.getCode(),       // "USER_NOT_FOUND"
                        ex.getMessage()     // "유저를 찾을 수 없습니다."
                ));
    }

    // 403
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiResponseDto<Void>> handleAccessDeniedException(AccessDeniedException ex) {
        ErrorCode errorCode = ErrorCode.USER_FORBIDDEN;
        return ResponseEntity
                .status(HttpStatus.FORBIDDEN)
                .body(ApiResponseDto.error(
                        errorCode.name(),       // "USER_FORBIDDEN"
                        errorCode.getMessage()  // "접근 권한이 없습니다."
                ));
    }

    // 401 - 인증 실패
    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ApiResponseDto<Void>> handleAuthenticationException(AuthenticationException ex) {
        ErrorCode errorCode = ErrorCode.USER_UNAUTHORIZED;
        return ResponseEntity
                .status(HttpStatus.UNAUTHORIZED)
                .body(ApiResponseDto.error(
                        errorCode.name(),
                        errorCode.getMessage()
                ));
    }

    // 401 - 토큰 만료
    @ExceptionHandler(ExpiredJwtException.class)
    public ResponseEntity<ApiResponseDto<Void>> handleExpiredJwtException(ExpiredJwtException ex) {
        return ResponseEntity
                .status(HttpStatus.UNAUTHORIZED)
                .body(ApiResponseDto.error(
                        "TOKEN_EXPIRED",
                        "토큰이 만료되었습니다."
                ));
    }

    // 401 - 잘못된 토큰 형식
    @ExceptionHandler(MalformedJwtException.class)
    public ResponseEntity<ApiResponseDto<Void>> handleMalformedJwtException(MalformedJwtException ex) {
        return ResponseEntity
                .status(HttpStatus.UNAUTHORIZED)
                .body(ApiResponseDto.error(
                        "TOKEN_MALFORMED",
                        "유효하지 않은 토큰입니다."
                ));
    }

    // 401 - 서명 오류
    @ExceptionHandler(SignatureException.class)
    public ResponseEntity<ApiResponseDto<Void>> handleSignatureException(SignatureException ex) {
        return ResponseEntity
                .status(HttpStatus.UNAUTHORIZED)
                .body(ApiResponseDto.error(
                        "TOKEN_SIGNATURE_INVALID",
                        "토큰 서명이 올바르지 않습니다."
                ));
    }

    // 400 - @Valid 유효성 검증 실패
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponseDto<Map<String, String>>> handleMethodArgumentNotValidException(
            MethodArgumentNotValidException ex) {
        Map<String, String> errors = new HashMap<>();
        ex.getBindingResult().getAllErrors().forEach(error -> {
            String fieldName = ((FieldError) error).getField();
            String errorMessage = error.getDefaultMessage();
            errors.put(fieldName, errorMessage);
        });
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ApiResponseDto.errorWithMap(
                        errors,
                        ErrorCode.INVALID_INPUT.name(),      // "INVALID_INPUT"
                        ErrorCode.INVALID_INPUT.getMessage()
                ));
    }

    // 400 - 필수 파라미터 누락
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ApiResponseDto<Void>> handleMissingServletRequestParameterException(
            MissingServletRequestParameterException ex) {
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ApiResponseDto.error(
                        ErrorCode.INVALID_INPUT.name(),
                        ex.getMessage()
                ));
    }

    // 500 - 그 외 모든 예외
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponseDto<Void>> handleException(Exception ex) {
        log.error("서버 내부 오류", ex);
        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponseDto.error(
                        ErrorCode.INTERNAL_SERVER_ERROR.name(),
                        ErrorCode.INTERNAL_SERVER_ERROR.getMessage()
                ));
    }
}
