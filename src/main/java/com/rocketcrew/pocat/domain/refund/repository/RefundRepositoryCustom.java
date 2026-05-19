package com.rocketcrew.pocat.domain.refund.repository;

import com.rocketcrew.pocat.domain.refund.dto.response.AdminRefundResponse;
import com.rocketcrew.pocat.domain.refund.dto.response.RefundResponse;
import com.rocketcrew.pocat.domain.refund.entity.RefundStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface RefundRepositoryCustom {

    /** 구매자 본인의 환불 목록 (orders JOIN으로 buyer_id 필터) */
    Page<RefundResponse> findMyRefunds(Long buyerId, RefundStatus status, Pageable pageable);

    /** 관리자 전체 환불 목록 (buyerNickname 포함 JOIN) */
    Page<AdminRefundResponse> findAdminRefunds(RefundStatus status, Pageable pageable);
}
