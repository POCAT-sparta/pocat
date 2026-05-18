package com.rocketcrew.pocat.domain.bid.service;

import com.rocketcrew.pocat.domain.bid.dto.request.CreateBidRequest;
import com.rocketcrew.pocat.domain.bid.dto.response.AuctionBidResponse;
import com.rocketcrew.pocat.domain.bid.entity.AuctionBid;
import com.rocketcrew.pocat.domain.bid.entity.BidStatus;
import com.rocketcrew.pocat.domain.bid.repository.AuctionBidRepository;
import com.rocketcrew.pocat.global.exception.common.ErrorCode;
import com.rocketcrew.pocat.global.exception.domain.BidException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
public class AuctionBidService {

    private final AuctionBidRepository auctionBidRepository;

    @Transactional(readOnly = true)
    public List<AuctionBidResponse> getBidsByAuction(Long auctionId) {
        return auctionBidRepository.findByAuctionId(auctionId).stream()
                .map(AuctionBidResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public AuctionBidResponse getBid(Long id) {
        AuctionBid auctionBid = auctionBidRepository.findById(id)
                .orElseThrow(() -> new BidException(ErrorCode.BID_NOT_FOUND));
        return AuctionBidResponse.from(auctionBid);
    }

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
