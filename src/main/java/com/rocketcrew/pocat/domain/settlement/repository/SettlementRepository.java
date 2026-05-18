package com.rocketcrew.pocat.domain.settlement.repository;

import com.rocketcrew.pocat.domain.settlement.entity.Settlement;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SettlementRepository extends JpaRepository<Settlement, Long> {

    Page<Settlement> findBySellerId(Long sellerId, Pageable pageable);
}
