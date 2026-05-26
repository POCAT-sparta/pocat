package com.rocketcrew.pocat.domain.auction.repository;

import com.rocketcrew.pocat.domain.auction.entity.Auction;
import com.rocketcrew.pocat.domain.auction.enums.AuctionStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface AuctionRepository extends JpaRepository<Auction, Long>, AuctionRepositoryCustom {

    List<Auction> findAllByStatus(AuctionStatus status);

    List<Auction> findByCardIdAndStatus(Long cardId, AuctionStatus status);

    @Query("SELECT a FROM Auction a WHERE a.cardId = :cardId AND a.status = :status AND a.endedAt >= :cutoffDate ORDER BY a.endedAt DESC")
    List<Auction> findCompletedByCardIdSince(@Param("cardId") Long cardId, @Param("status") AuctionStatus status, @Param("cutoffDate") LocalDateTime cutoffDate, Pageable pageable);
}
