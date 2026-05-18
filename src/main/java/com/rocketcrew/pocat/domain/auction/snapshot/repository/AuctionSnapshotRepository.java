package com.rocketcrew.pocat.domain.auction.snapshot.repository;

import com.rocketcrew.pocat.domain.auction.snapshot.entity.AuctionSnapshot;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AuctionSnapshotRepository extends JpaRepository<AuctionSnapshot, Long> {

    Optional<AuctionSnapshot> findByAuctionId(Long auctionId);
}
