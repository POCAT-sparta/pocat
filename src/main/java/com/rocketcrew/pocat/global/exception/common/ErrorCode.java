package com.rocketcrew.pocat.global.exception.common;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum ErrorCode {

    // Auth
    INVALID_REFRESH_TOKEN(HttpStatus.UNAUTHORIZED, "유효하지 않은 리프레시 토큰입니다."),
    INVALID_ACCESS_TOKEN(HttpStatus.UNAUTHORIZED, "유효하지 않은 액세스 토큰입니다."),

    // User
    USER_NOT_FOUND(HttpStatus.NOT_FOUND, "유저를 찾을 수 없습니다."),
    EMAIL_ALREADY_EXISTS(HttpStatus.CONFLICT, "이미 존재하는 이메일입니다."),
    USER_INFO_MISMATCH(HttpStatus.BAD_REQUEST, "유저의 정보가 일치하지 않습니다."),
    USER_FORBIDDEN(HttpStatus.FORBIDDEN, "접근 권한이 없습니다"),
    USER_UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "인증되지 않은 유저입니다."),

    // Card
    CARD_NOT_FOUND(HttpStatus.NOT_FOUND, "카드를 찾을 수 없습니다."),

    // Auction
    AUCTION_NOT_FOUND(HttpStatus.NOT_FOUND, "경매를 찾을 수 없습니다."),

    // Bid
    BID_NOT_FOUND(HttpStatus.NOT_FOUND, "입찰을 찾을 수 없습니다."),

    // Order
    ORDER_NOT_FOUND(HttpStatus.NOT_FOUND, "주문을 찾을 수 없습니다."),
    ORDER_ALREADY_CANCELLED(HttpStatus.CONFLICT, "이미 취소된 주문입니다."),
    ORDER_CANNOT_CANCEL(HttpStatus.BAD_REQUEST, "취소할 수 없는 주문 상태입니다."),

    // Payment
    PAYMENT_NOT_FOUND(HttpStatus.NOT_FOUND, "결제를 찾을 수 없습니다."),
    PAYMENT_ORDER_NOT_FAILED(HttpStatus.CONFLICT, "결제 요청은 주문 상태가 PAYMENT_FAILED일 때만 가능합니다."),
    PAYMENT_WINDOW_EXPIRED(HttpStatus.GONE, "결제 가능 시간(1시간)이 초과되었습니다."),
    PAYMENT_BUYER_MISMATCH(HttpStatus.FORBIDDEN, "결제 요청자와 주문 구매자가 일치하지 않습니다."),
    PAYMENT_AMOUNT_MISMATCH(HttpStatus.CONFLICT, "결제 금액이 주문 금액과 일치하지 않습니다."),
    WEBHOOK_SIGNATURE_INVALID(HttpStatus.FORBIDDEN, "Webhook 서명 검증에 실패했습니다."),

    // Refund
    REFUND_NOT_FOUND(HttpStatus.NOT_FOUND, "환불을 찾을 수 없습니다."),

    // Settlement
    SETTLEMENT_NOT_FOUND(HttpStatus.NOT_FOUND, "정산을 찾을 수 없습니다."),

    // FreePost
    FREE_POST_NOT_FOUND(HttpStatus.NOT_FOUND, "자유 게시글을 찾을 수 없습니다."),

    // TradePost
    TRADE_POST_NOT_FOUND(HttpStatus.NOT_FOUND, "거래 게시글을 찾을 수 없습니다."),

    // Comment
    COMMENT_NOT_FOUND(HttpStatus.NOT_FOUND, "댓글을 찾을 수 없습니다."),

    // Notification
    NOTIFICATION_NOT_FOUND(HttpStatus.NOT_FOUND, "알림을 찾을 수 없습니다."),

    // Chat
    CHAT_NOT_FOUND(HttpStatus.NOT_FOUND, "채팅을 찾을 수 없습니다."),

    // Common
    INVALID_INPUT(HttpStatus.BAD_REQUEST, "잘못된 입력값입니다."),
    INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "서버 내부 오류가 발생했습니다.");

    private final HttpStatus status;
    private final String message;
}
