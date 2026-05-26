package com.rocketcrew.pocat.domain.auction.repository;

import com.rocketcrew.pocat.domain.auction.entity.Auction;
import com.rocketcrew.pocat.domain.auction.enums.AuctionStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AuctionRepository extends JpaRepository<Auction, Long>, AuctionRepositoryCustom {

    List<Auction> findAllByStatus(AuctionStatus status);

    List<Auction> findByCardIdAndStatus(Long cardId, AuctionStatus status);

    List<Auction> findByCardIdAndStatusOrderByEndedAtDesc(Long cardId, AuctionStatus status, Pageable pageable);
}
