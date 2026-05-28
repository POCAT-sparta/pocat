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

    @Query("""
            SELECT a FROM Auction a
            WHERE a.status = :status
              AND (
                  EXISTS (SELECT 1 FROM Like l WHERE l.auctionId = a.id)
                  OR EXISTS (SELECT 1 FROM AuctionBid b WHERE b.auctionId = a.id)
              )
            ORDER BY a.startedAt DESC, a.id DESC
            """)
    List<Auction> findByStatusWithPopularitySignal(
            @Param("status") AuctionStatus status
    );

    List<Auction> findByStatusOrderByStartedAtDescIdDesc(AuctionStatus status, Pageable pageable);

    // status가 ACTIVE이고 endedAt이 전달된 시각 이하인 경매를 종료 예정 시각 오름차순으로 모두 조회한다.
    // 백업 스케줄러가 Redis 만료 이벤트로 처리되지 않은 경매를 보정 종료할 때 사용한다.
    List<Auction> findAllByStatusAndEndedAtLessThanEqualOrderByEndedAtAsc(
            AuctionStatus status,
            LocalDateTime endedAt
    );

    List<Auction> findByCardIdAndStatus(Long cardId, AuctionStatus status);

    List<Auction> findByCardIdInAndStatus(List<Long> cardIds, AuctionStatus status);

    @Query("SELECT a FROM Auction a WHERE a.cardId = :cardId AND a.status = :status AND a.endedAt >= :cutoffDate ORDER BY a.endedAt DESC")
    List<Auction> findCompletedByCardIdSince(@Param("cardId") Long cardId, @Param("status") AuctionStatus status, @Param("cutoffDate") LocalDateTime cutoffDate, Pageable pageable);
}
