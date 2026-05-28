package com.rocketcrew.pocat.domain.refund.entity;

public enum RefundStatus {
    REQUESTED,       // 환불 요청됨 (관리자 미처리)
    PROCESSING,      // 관리자 승인 후 PortOne 취소 호출 중
    COMPLETED,       // 환불 완료
    REJECTED,        // 관리자 거절
    FAILED_RETRYABLE, // PortOne 취소 실패 — 자동 재시도 예정
    FAILED_FINAL     // 자동 재시도 한도 초과 — 수동 처리 필요
}
