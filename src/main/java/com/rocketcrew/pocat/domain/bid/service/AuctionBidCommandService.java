package com.rocketcrew.pocat.domain.bid.service;

import com.rocketcrew.pocat.domain.bid.dto.request.CreateBidRequest;
import com.rocketcrew.pocat.domain.bid.dto.response.CreateAuctionBidResponse;
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

    public CreateAuctionBidResponse createBid(Long userId, Long auctionId, CreateBidRequest request) {
        // Todo :  입찰가 검증, 직전 최고 입찰자 알림,
        AuctionBid auctionBid = AuctionBid.builder()
                .userId(userId)
                .auctionId(auctionId)
                .bidPrice(request.bidPrice())
                .status(BidStatus.ACTIVE)
                .build();
        return CreateAuctionBidResponse.from(auctionBidRepository.save(auctionBid));
    }
}
