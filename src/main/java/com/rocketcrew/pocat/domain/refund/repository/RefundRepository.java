package com.rocketcrew.pocat.domain.refund.repository;

import com.rocketcrew.pocat.domain.refund.entity.Refund;
import com.rocketcrew.pocat.domain.refund.entity.RefundStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface RefundRepository extends JpaRepository<Refund, Long>, RefundRepositoryCustom {

    /** 중복 환불 방지: 동일 주문에 REQUESTED 또는 COMPLETED 상태 환불 존재 여부 확인 */
    boolean existsByOrderIdAndStatusIn(Long orderId, List<RefundStatus> statuses);
}
