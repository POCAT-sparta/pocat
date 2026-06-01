package com.rocketcrew.pocat.domain.order.service;

public record EscalationResult(Status status, Long nextBidderId) {

    public enum Status {
        ESCALATED,  // 다음 입찰자에게 직접결제 기간 부여
        CANCELLED,  // 다음 입찰자 없음 또는 최대 순위 초과 → 경매 취소
        SKIPPED     // 주문 없음, 상태 불일치, 즉시구매 등 처리 불가
    }

    public static EscalationResult escalated(Long nextBidderId) {
        return new EscalationResult(Status.ESCALATED, nextBidderId);
    }

    public static EscalationResult cancelled() {
        return new EscalationResult(Status.CANCELLED, null);
    }

    public static EscalationResult skipped() {
        return new EscalationResult(Status.SKIPPED, null);
    }
}
