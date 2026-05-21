package com.rocketcrew.pocat.domain.bid.repository;

import com.rocketcrew.pocat.domain.bid.entity.AuctionBid;
import com.rocketcrew.pocat.domain.bid.enums.BidStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AuctionBidRepository extends JpaRepository<AuctionBid, Long>, AuctionBidRepositoryCustom {

    Optional<AuctionBid> findFirstByAuctionIdAndUserIdAndStatusOrderByBidPriceDescCreatedAtDesc(
            Long auctionId,
            Long userId,
            BidStatus status
    );
}
