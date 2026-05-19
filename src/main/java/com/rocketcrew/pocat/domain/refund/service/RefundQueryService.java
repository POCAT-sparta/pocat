package com.rocketcrew.pocat.domain.refund.service;

import com.rocketcrew.pocat.domain.order.entity.Order;
import com.rocketcrew.pocat.domain.order.repository.OrderRepository;
import com.rocketcrew.pocat.domain.refund.dto.response.AdminRefundResponse;
import com.rocketcrew.pocat.domain.refund.dto.response.RefundResponse;
import com.rocketcrew.pocat.domain.refund.entity.Refund;
import com.rocketcrew.pocat.domain.refund.entity.RefundStatus;
import com.rocketcrew.pocat.domain.refund.repository.RefundRepository;
import com.rocketcrew.pocat.global.exception.common.ErrorCode;
import com.rocketcrew.pocat.global.exception.domain.OrderException;
import com.rocketcrew.pocat.global.exception.domain.RefundException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RefundQueryService {

    private final RefundRepository refundRepository;
    private final OrderRepository orderRepository;

    /**
     * 7.2 내 환불 내역 조회
     * buyer_id 기준으로 orders JOIN 후 refunds 필터링.
     * status 미입력 시 전체 조회.
     */
    public Page<RefundResponse> getMyRefunds(Long buyerId, RefundStatus status, Pageable pageable) {
        return refundRepository.findMyRefunds(buyerId, status, pageable);
    }

    /**
     * 7.3 환불 상세 조회
     * 해당 주문의 buyer 또는 ADMIN만 접근 가능.
     */
    public RefundResponse getRefund(Long requesterId, Long refundId) {
        Refund refund = findRefund(refundId);
        Order order = findOrder(refund.getOrderId());

        if (!isAdmin() && !order.getBuyerId().equals(requesterId)) {
            throw new RefundException(ErrorCode.REFUND_BUYER_MISMATCH);
        }

        return RefundResponse.from(refund);
    }

    /**
     * 7.6 전체 환불 목록 (ADMIN)
     * QueryDSL Projections로 buyerNickname 포함 DTO 직접 조회.
     */
    public Page<AdminRefundResponse> getAdminRefunds(RefundStatus status, Pageable pageable) {
        return refundRepository.findAdminRefunds(status, pageable);
    }

    // ── 내부 헬퍼 ────────────────────────────────────────────────────

    private Refund findRefund(Long refundId) {
        return refundRepository.findById(refundId)
                .orElseThrow(() -> new RefundException(ErrorCode.REFUND_NOT_FOUND));
    }

    private Order findOrder(Long orderId) {
        return orderRepository.findById(orderId)
                .orElseThrow(() -> new OrderException(ErrorCode.ORDER_NOT_FOUND));
    }

    private boolean isAdmin() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null && auth.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
    }
}
