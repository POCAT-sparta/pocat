package com.rocketcrew.pocat.domain.bid.service;

import com.rocketcrew.pocat.domain.bid.dto.response.AuctionBidHistoryResponse;
import com.rocketcrew.pocat.domain.bid.repository.AuctionBidRepository;
import com.rocketcrew.pocat.global.cache.CacheNames;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AuctionBidCacheService {

    private final AuctionBidRepository auctionBidRepository;

    @Cacheable(
            value = CacheNames.AUCTION_BID_HISTORY,
            key = "#auctionId + ':page:' + #pageable.pageNumber + ':size:' + #pageable.pageSize",
            sync = true
    )
    public Page<AuctionBidHistoryResponse> getBidHistoryByEndedAuction(Long auctionId, Pageable pageable) {
        return auctionBidRepository.findBidHistoryByAuctionId(auctionId, pageable);
    }
}
