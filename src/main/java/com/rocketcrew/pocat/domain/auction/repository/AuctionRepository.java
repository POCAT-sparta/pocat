package com.rocketcrew.pocat.domain.auction.repository;

import com.rocketcrew.pocat.domain.auction.entity.Auction;
import com.rocketcrew.pocat.domain.auction.enums.AuctionStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AuctionRepository extends JpaRepository<Auction, Long> {

    Page<Auction> findByStatus(AuctionStatus status, Pageable pageable);

    Page<Auction> findBySellerId(Long sellerId, Pageable pageable);
}
