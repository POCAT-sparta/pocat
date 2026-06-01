package com.rocketcrew.pocat.domain.order.service;

public record EscalationResult(Status status, Long nextBidderId, String nextOrderUid) {

    public EscalationResult {
        if (status == Status.ESCALATED && nextBidderId == null) {
            throw new IllegalArgumentException("ESCALATED 상태는 nextBidderId가 반드시 필요합니다");
        }
        if (status == Status.ESCALATED && nextOrderUid == null) {
            throw new IllegalArgumentException("ESCALATED 상태는 nextOrderUid가 반드시 필요합니다");
        }
        if (status != Status.ESCALATED && nextBidderId != null) {
            throw new IllegalArgumentException(status + " 상태는 nextBidderId가 null이어야 합니다");
        }
        if (status != Status.ESCALATED && nextOrderUid != null) {
            throw new IllegalArgumentException(status + " 상태는 nextOrderUid가 null이어야 합니다");
        }
    }

    public enum Status {
        ESCALATED,  // 다음 입찰자에게 직접결제 기간 부여
        CANCELLED,  // 다음 입찰자 없음 또는 최대 순위 초과 → 경매 취소
        SKIPPED     // 주문 없음, 상태 불일치, 즉시구매 등 처리 불가
    }

    public static EscalationResult escalated(Long nextBidderId, String nextOrderUid) {
        return new EscalationResult(Status.ESCALATED, nextBidderId, nextOrderUid);
    }

    public static EscalationResult cancelled() {
        return new EscalationResult(Status.CANCELLED, null, null);
    }

    public static EscalationResult skipped() {
        return new EscalationResult(Status.SKIPPED, null, null);
    }
}
