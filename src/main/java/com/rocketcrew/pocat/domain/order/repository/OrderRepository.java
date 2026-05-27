package com.rocketcrew.pocat.domain.order.repository;

import com.rocketcrew.pocat.domain.order.entity.Order;
import com.rocketcrew.pocat.domain.order.enums.OrderStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Optional;

public interface OrderRepository extends JpaRepository<Order, Long>, OrderRepositoryCustom {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT o FROM Order o WHERE o.id = :id")
    Optional<Order> findByIdWithLock(@Param("id") Long id);

    Page<Order> findByBuyerId(Long buyerId, Pageable pageable);

    Page<Order> findByBuyerIdAndStatus(Long buyerId, OrderStatus status, Pageable pageable);

    Optional<Order> findByOrderUid(String orderUid);

    @Query("SELECT AVG(o.finalPrice), COUNT(o) FROM Order o " +
           "WHERE o.cardId = :cardId AND o.status = :status AND o.createdAt >= :since")
    Object[] findAvgAndCountByCardId(
            @Param("cardId") Long cardId,
            @Param("status") OrderStatus status,
            @Param("since") LocalDateTime since
    );

    Optional<Order> findByAuctionIdAndBidderRank(Long auctionId, Integer bidderRank);

}
