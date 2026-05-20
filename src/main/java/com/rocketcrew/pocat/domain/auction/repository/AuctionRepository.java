package com.rocketcrew.pocat.domain.auction.repository;

import com.rocketcrew.pocat.domain.auction.entity.Auction;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AuctionRepository extends JpaRepository<Auction, Long>, AuctionRepositoryCustom {
}
