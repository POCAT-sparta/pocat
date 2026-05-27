package com.rocketcrew.pocat.domain.bid.service;

import com.rocketcrew.pocat.domain.auction.entity.Auction;
import com.rocketcrew.pocat.domain.auction.enums.AuctionStatus;
import com.rocketcrew.pocat.domain.auction.repository.AuctionRepository;
import com.rocketcrew.pocat.domain.bid.dto.response.AuctionBidHistoryResponse;
import com.rocketcrew.pocat.domain.bid.dto.response.MyBidResponse;
import com.rocketcrew.pocat.domain.bid.enums.BidStatus;
import com.rocketcrew.pocat.domain.bid.repository.AuctionBidRepository;
import com.rocketcrew.pocat.global.exception.common.ErrorCode;
import com.rocketcrew.pocat.global.exception.domain.AuctionException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AuctionBidQueryService {

    private final AuctionBidRepository auctionBidRepository;
    private final AuctionRepository auctionRepository;
    private final AuctionBidCacheService auctionBidCacheService;

    public Page<AuctionBidHistoryResponse> getBidHistoryByAuction(Long auctionId, Pageable pageable) {
        Auction auction = auctionRepository.findById(auctionId)
                .orElseThrow(() -> new AuctionException(ErrorCode.AUCTION_NOT_FOUND));

        AuctionStatus status = auction.getStatus();
        if (status == AuctionStatus.ENDED || status == AuctionStatus.NO_BIDDER) {
            return auctionBidCacheService.getBidHistoryByEndedAuction(auctionId, pageable);
        }

        if (status != AuctionStatus.ACTIVE) {
            throw new AuctionException(ErrorCode.AUCTION_NOT_ACTIVE);
        }

        return auctionBidRepository.findBidHistoryByAuctionId(auctionId, pageable);
    }

    public Page<MyBidResponse> getMyBids(Long userId, BidStatus status, Pageable pageable) {
        return auctionBidRepository.findMyBids(userId, status, pageable);
    }
}
