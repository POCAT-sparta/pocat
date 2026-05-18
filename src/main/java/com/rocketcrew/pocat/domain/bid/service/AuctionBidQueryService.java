package com.rocketcrew.pocat.domain.bid.service;

import com.rocketcrew.pocat.domain.bid.dto.response.AuctionBidResponse;
import com.rocketcrew.pocat.domain.bid.entity.AuctionBid;
import com.rocketcrew.pocat.domain.bid.repository.AuctionBidRepository;
import com.rocketcrew.pocat.global.exception.common.ErrorCode;
import com.rocketcrew.pocat.global.exception.domain.BidException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AuctionBidQueryService {

    private final AuctionBidRepository auctionBidRepository;

    public List<AuctionBidResponse> getBidsByAuction(Long auctionId) {
        return auctionBidRepository.findByAuctionId(auctionId).stream()
                .map(AuctionBidResponse::from)
                .toList();
    }

    public AuctionBidResponse getBid(Long id) {
        AuctionBid auctionBid = auctionBidRepository.findById(id)
                .orElseThrow(() -> new BidException(ErrorCode.BID_NOT_FOUND));
        return AuctionBidResponse.from(auctionBid);
    }
}
