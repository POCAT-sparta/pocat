package com.rocketcrew.pocat.domain.bid.repository;

import com.rocketcrew.pocat.domain.bid.entity.AuctionBid;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AuctionBidRepository extends JpaRepository<AuctionBid, Long>, AuctionBidRepositoryCustom {
}
