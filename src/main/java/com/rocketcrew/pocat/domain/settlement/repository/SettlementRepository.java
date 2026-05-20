package com.rocketcrew.pocat.domain.settlement.repository;

import com.rocketcrew.pocat.domain.settlement.entity.Settlement;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

import java.util.Optional;

public interface SettlementRepository extends JpaRepository<Settlement, Long>, SettlementRepositoryCustom {

    boolean existsByOrderId(Long orderId);

    Page<Settlement> findBySellerId(Long sellerId, Pageable pageable);

    Optional<Settlement> findBySettlementUidAndSellerId(String settlementUid, Long sellerId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<Settlement> findWithLockBySettlementUid(String settlementUid);
}
