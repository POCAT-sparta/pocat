package com.rocketcrew.pocat.domain.bid.service;

import com.rocketcrew.pocat.domain.bid.dto.request.CreateBidRequest;
import com.rocketcrew.pocat.domain.bid.dto.response.AuctionBidResponse;
import com.rocketcrew.pocat.domain.bid.entity.AuctionBid;
import com.rocketcrew.pocat.domain.bid.enums.BidStatus;
import com.rocketcrew.pocat.domain.bid.repository.AuctionBidRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class AuctionBidCommandService {

    private final AuctionBidRepository auctionBidRepository;

    public AuctionBidResponse createBid(Long userId, CreateBidRequest request) {
        AuctionBid auctionBid = AuctionBid.builder()
                .userId(userId)
                .auctionId(request.auctionId())
                .bidPrice(request.bidPrice())
                .status(BidStatus.ACTIVE)
                .build();
        return AuctionBidResponse.from(auctionBidRepository.save(auctionBid));
    }
}
