package com.rocketcrew.pocat.domain.refund.repository;

import com.rocketcrew.pocat.domain.refund.entity.Refund;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RefundRepository extends JpaRepository<Refund, Long> {

    Page<Refund> findByOrderId(Long orderId, Pageable pageable);
}
