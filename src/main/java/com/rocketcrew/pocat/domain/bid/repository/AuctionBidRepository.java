package com.rocketcrew.pocat.domain.bid.repository;

import com.rocketcrew.pocat.domain.bid.entity.AuctionBid;
import com.rocketcrew.pocat.domain.bid.enums.BidStatus;
import com.rocketcrew.pocat.domain.auction.ranking.dto.AuctionCountProjection;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface AuctionBidRepository extends JpaRepository<AuctionBid, Long>, AuctionBidRepositoryCustom {

    // 경매 종료 시 해당 경매의 모든 입찰 상태를 WON/LOST로 정리하기 위해 조회한다.
    List<AuctionBid> findAllByAuctionId(Long auctionId);

    // 즉시구매 결제 완료 시 OUTBID 상태인 입찰자를 LOST로 일괄 처리하기 위해 조회한다.
    List<AuctionBid> findAllByAuctionIdAndStatus(Long auctionId, BidStatus status);

    Optional<AuctionBid> findFirstByAuctionIdAndUserIdAndStatusOrderByBidPriceDescCreatedAtDesc(
            Long auctionId,
            Long userId,
            BidStatus status
    );

    Optional<AuctionBid> findFirstByAuctionIdAndStatusOrderByBidPriceDescCreatedAtDesc(
            Long auctionId,
            BidStatus status
    );

    @Query("SELECT b.auctionId AS auctionId, COUNT(b) AS cnt FROM AuctionBid b WHERE b.auctionId IN :ids GROUP BY b.auctionId")
    List<AuctionCountProjection> countByAuctionIdIn(@Param("ids") List<Long> ids);

    List<AuctionBid> findByUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);
}
