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
    LOGIN_LOCKED(HttpStatus.TOO_MANY_REQUESTS, "로그인 시도 횟수를 초과했습니다. 5분 후 다시 시도해주세요."),

    // User
    USER_NOT_FOUND(HttpStatus.NOT_FOUND, "유저를 찾을 수 없습니다."),
    EMAIL_ALREADY_EXISTS(HttpStatus.CONFLICT, "이미 존재하는 이메일입니다."),
    USER_INFO_MISMATCH(HttpStatus.BAD_REQUEST, "유저의 정보가 일치하지 않습니다."),
    USER_FORBIDDEN(HttpStatus.FORBIDDEN, "접근 권한이 없습니다"),
    USER_UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "인증되지 않은 유저입니다."),

    // Card
    CARD_NOT_FOUND(HttpStatus.NOT_FOUND, "카드를 찾을 수 없습니다."),
    CARD_NOT_ACTIVE(HttpStatus.CONFLICT, "ACTIVE 상태의 카드만 경매에 등록할 수 있습니다."),
    CARD_ALREADY_EXISTS(HttpStatus.CONFLICT, "이미 등록된 카드입니다."),

    // Auction
    AUCTION_NOT_ACTIVE(HttpStatus.CONFLICT, "ACTIVE 상태의 경매만 가능합니다."),
    AUCTION_NOT_FOUND(HttpStatus.NOT_FOUND, "경매를 찾을 수 없습니다."),
    AUCTION_NOT_PENDING(HttpStatus.CONFLICT,"경매가 PENDING 상태가 아닙니다."),
    AUCTION_PRICE_INVALID(HttpStatus.BAD_REQUEST, "유효하지 않은 경매 가격입니다."),
    AUCTION_UPDATE_EMPTY(HttpStatus.BAD_REQUEST, "수정할 필드가 하나 이상 필요합니다."),
    AUCTION_CARD_NOT_FOUND(HttpStatus.NOT_FOUND, "경매에 연결된 카드를 찾을 수 없습니다."),
    AUCTION_CARD_NOT_ACTIVE(HttpStatus.CONFLICT, "ACTIVE 상태의 카드만 경매에 등록할 수 있습니다."),
    AUCTION_SELLER_NOT_FOUND(HttpStatus.NOT_FOUND, "경매 판매자를 찾을 수 없습니다."),
    AUCTION_HIGHEST_BIDDER_NOT_FOUND(HttpStatus.NOT_FOUND, "경매 최고 입찰자를 찾을 수 없습니다."),
    AUCTION_NOT_INSPECTING(HttpStatus.CONFLICT, "PENDING 또는 INSPECTING 상태의 경매만 검수할 수 있습니다."),
    AUCTION_REASON_REQUIRED(HttpStatus.BAD_REQUEST, "사유를 입력해주세요."),
    AUCTION_CANNOT_CANCEL(HttpStatus.CONFLICT, "취소할 수 없는 경매 상태입니다."),
    AUCTION_LOCK_FAILED(HttpStatus.CONFLICT, "다른 경매 처리가 진행 중입니다. 잠시 후 다시 시도해주세요."),
    AUCTION_INVALID_STATUS_TRANSITION(HttpStatus.CONFLICT, "변경할 수 없는 경매 상태입니다."),

    // Bid
    BID_NOT_FOUND(HttpStatus.NOT_FOUND, "입찰을 찾을 수 없습니다."),
    BID_BLOCKED_USER(HttpStatus.FORBIDDEN, "입찰이 차단된 사용자입니다."),
    BID_SELLER_FORBIDDEN(HttpStatus.FORBIDDEN, "판매자는 본인 경매에 입찰할 수 없습니다."),
    BID_BILLING_KEY_REQUIRED(HttpStatus.BAD_REQUEST, "입찰 전 결제수단/빌링키 등록이 필요합니다."),
    BID_LOCK_FAILED(HttpStatus.CONFLICT, "다른 입찰이 처리 중입니다. 잠시 후 다시 시도해주세요."),
    BID_PRICE_TOO_LOW(HttpStatus.CONFLICT, "입찰가는 최소 입찰가와 현재 최고가보다 높아야 합니다."),
    BID_BUYOUT_PRICE_NOT_ALLOWED(HttpStatus.BAD_REQUEST, "즉시구매가 이상 금액은 즉시구매 API를 호출해야 합니다."),
    BID_ALREADY_LEADING(HttpStatus.CONFLICT, "이미 현재 최고 입찰자입니다."),
    BID_INVALID_STATUS_TRANSITION(HttpStatus.CONFLICT, "변경할 수 없는 입찰 상태입니다."),

    // Order
    ORDER_NOT_FOUND(HttpStatus.NOT_FOUND, "주문을 찾을 수 없습니다."),
    ORDER_FORBIDDEN(HttpStatus.FORBIDDEN, "해당 주문에 대한 접근 권한이 없습니다."),
    ORDER_ALREADY_CANCELLED(HttpStatus.CONFLICT, "이미 취소된 주문입니다."),
    ORDER_CANNOT_CANCEL(HttpStatus.BAD_REQUEST, "취소할 수 없는 주문 상태입니다."),
    ORDER_CANNOT_COMPLETE_PAYMENT(HttpStatus.CONFLICT, "결제 완료 처리가 불가능한 주문 상태입니다."),
    ORDER_CANNOT_FAIL_PAYMENT(HttpStatus.CONFLICT, "결제 실패 처리가 불가능한 주문 상태입니다."),

    // Payment
    PAYMENT_NOT_FOUND(HttpStatus.NOT_FOUND, "결제를 찾을 수 없습니다."),
    PAYMENT_ORDER_NOT_FAILED(HttpStatus.CONFLICT, "결제 요청은 주문 상태가 PAYMENT_FAILED일 때만 가능합니다."),
    PAYMENT_WINDOW_EXPIRED(HttpStatus.GONE, "결제 가능 시간(1시간)이 초과되었습니다."),
    PAYMENT_BUYER_MISMATCH(HttpStatus.FORBIDDEN, "결제 요청자와 주문 구매자가 일치하지 않습니다."),
    PAYMENT_AMOUNT_MISMATCH(HttpStatus.CONFLICT, "결제 금액이 주문 금액과 일치하지 않습니다."),
    PAYMENT_METHOD_REQUIRED(HttpStatus.BAD_REQUEST, "결제 수단은 필수입니다."),
    PAYMENT_PAID_AT_REQUIRED(HttpStatus.BAD_REQUEST, "결제 완료 시각은 필수입니다."),
    PAYMENT_CANNOT_COMPLETE(HttpStatus.CONFLICT, "결제 완료 처리가 불가능한 결제 상태입니다."),
    PAYMENT_CANNOT_FAIL(HttpStatus.CONFLICT, "결제 실패 처리가 불가능한 결제 상태입니다."),
    PAYMENT_CANNOT_REFUND(HttpStatus.CONFLICT, "환불 처리가 불가능한 결제 상태입니다."),
    WEBHOOK_SIGNATURE_INVALID(HttpStatus.FORBIDDEN, "Webhook 서명 검증에 실패했습니다."),
    WEBHOOK_EMPTY_BODY(HttpStatus.BAD_REQUEST, "Webhook 요청 본문이 비어 있습니다. 인프라 설정을 확인하세요."),
    PORTONE_NOT_INTEGRATED(HttpStatus.SERVICE_UNAVAILABLE, "PortOne 결제 연동이 완료되지 않았습니다."),
    PAYMENT_STATUS_NOT_PAID(HttpStatus.BAD_REQUEST, "PortOne 결제 상태가 PAID가 아닙니다."),

    // Refund
    REFUND_NOT_FOUND(HttpStatus.NOT_FOUND, "환불을 찾을 수 없습니다."),
    REFUND_INVALID_ORDER_STATUS(HttpStatus.CONFLICT, "환불 요청은 결제완료·배송중·완료 상태의 주문만 가능합니다."),
    REFUND_ALREADY_EXISTS(HttpStatus.CONFLICT, "동일 주문에 이미 진행 중이거나 완료된 환불이 존재합니다."),
    REFUND_BUYER_MISMATCH(HttpStatus.FORBIDDEN, "환불 요청자와 주문 구매자가 일치하지 않습니다."),
    REFUND_NOT_REQUESTED(HttpStatus.CONFLICT, "REQUESTED 상태의 환불만 처리할 수 있습니다."),

    // Settlement
    SETTLEMENT_NOT_FOUND(HttpStatus.NOT_FOUND, "정산을 찾을 수 없습니다."),
    SETTLEMENT_ALREADY_COMPLETED(HttpStatus.CONFLICT, "이미 정산이 완료된 건입니다."),
    SETTLEMENT_CANNOT_COMPLETE(HttpStatus.CONFLICT, "PENDING 상태의 정산만 완료 처리할 수 있습니다."),

    // FreePost
    FREE_POST_NOT_FOUND(HttpStatus.NOT_FOUND, "자유 게시글을 찾을 수 없습니다."),

    // TradePost
    TRADE_POST_NOT_FOUND(HttpStatus.NOT_FOUND, "거래 게시글을 찾을 수 없습니다."),

    // Comment
    COMMENT_NOT_FOUND(HttpStatus.NOT_FOUND, "댓글을 찾을 수 없습니다."),

    // Notification
    NOTIFICATION_NOT_FOUND(HttpStatus.NOT_FOUND, "알림을 찾을 수 없습니다."),
    NOTIFICATION_ACCESS_DENIED(HttpStatus.FORBIDDEN, "해당 알림에 대한 접근 권한이 없습니다."),
    NOTIFICATION_SERIALIZE_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "알림 직렬화에 실패했습니다."),

    // Chat
    CHAT_NOT_FOUND(HttpStatus.NOT_FOUND, "채팅방을 찾을 수 없습니다."),
    CHAT_FORBIDDEN(HttpStatus.FORBIDDEN, "채팅방 접근 권한이 없습니다."),
    CHAT_SELF_CHAT(HttpStatus.BAD_REQUEST, "자신의 게시글에는 채팅을 시작할 수 없습니다."),
    CHAT_ALREADY_EXISTS(HttpStatus.CONFLICT, "해당 게시글에 이미 채팅방이 존재합니다."),

    // Billing
    BILLING_KEY_ALREADY_EXISTS(HttpStatus.CONFLICT, "이미 등록된 빌링키가 존재합니다."),
    BILLING_KEY_NOT_FOUND(HttpStatus.NOT_FOUND, "등록된 빌링키가 없습니다."),

    // Validation
    INVALID_PARENT_COMMENT(HttpStatus.BAD_REQUEST, "유효하지 않은 부모 댓글입니다."),
    INVALID_CONTENT(HttpStatus.BAD_REQUEST, "내용은 비어 있을 수 없습니다."),

    // Common
    INVALID_INPUT(HttpStatus.BAD_REQUEST, "잘못된 입력값입니다."),
    INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "서버 내부 오류가 발생했습니다.");

    private final HttpStatus status;
    private final String message;
}
