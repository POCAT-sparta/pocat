package com.rocketcrew.pocat.domain.bid.repository;

import com.rocketcrew.pocat.domain.bid.entity.AuctionBid;
import com.rocketcrew.pocat.domain.bid.enums.BidStatus;
import com.rocketcrew.pocat.domain.auction.ranking.dto.AuctionCountProjection;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface AuctionBidRepository extends JpaRepository<AuctionBid, Long>, AuctionBidRepositoryCustom {

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
}
