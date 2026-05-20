package com.rocketcrew.pocat.domain.order.snapshot.repository;

import com.rocketcrew.pocat.domain.order.snapshot.entity.OrderSnapshot;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface OrderSnapshotRepository extends JpaRepository<OrderSnapshot, Long> {

    Optional<OrderSnapshot> findByOrderUid(String orderUid);
}
