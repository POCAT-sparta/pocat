package com.rocketcrew.pocat.domain.bid.repository;

import com.rocketcrew.pocat.domain.bid.dto.response.AuctionBidHistoryResponse;
import com.rocketcrew.pocat.domain.bid.dto.response.MyBidResponse;
import com.rocketcrew.pocat.domain.bid.enums.BidStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;

public interface AuctionBidRepositoryCustom {

    Page<MyBidResponse> findMyBids(Long userId, BidStatus status, Pageable pageable);

    Page<AuctionBidHistoryResponse> findBidHistoryByAuctionId(Long auctionId, Pageable pageable);

    List<Long> findDistinctBidderIdsByAuctionId(Long auctionId);

    // auctionId의 LOST 입찰자를 최고 입찰가 기준 내림차순으로 조회 (2순위, 3순위... 순서)
    List<Long> findLostBidderIdsByAuctionIdOrderedByMaxBidPrice(Long auctionId);
}
