package com.rocketcrew.pocat.domain.auction.service;

import com.rocketcrew.pocat.domain.auction.dto.request.CreateAuctionRequest;
import com.rocketcrew.pocat.domain.auction.dto.response.AuctionResponse;
import com.rocketcrew.pocat.domain.auction.entity.Auction;
import com.rocketcrew.pocat.domain.auction.entity.AuctionStatus;
import com.rocketcrew.pocat.domain.auction.repository.AuctionRepository;
import com.rocketcrew.pocat.global.exception.common.ErrorCode;
import com.rocketcrew.pocat.global.exception.domain.AuctionException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class AuctionService {

    private final AuctionRepository auctionRepository;

    @Transactional(readOnly = true)
    public Page<AuctionResponse> getAuctions(Pageable pageable) {
        return auctionRepository.findAll(pageable)
                .map(AuctionResponse::from);
    }

    @Transactional(readOnly = true)
    public AuctionResponse getAuction(Long id) {
        Auction auction = auctionRepository.findById(id)
                .orElseThrow(() -> new AuctionException(ErrorCode.AUCTION_NOT_FOUND));
        return AuctionResponse.from(auction);
    }

    public AuctionResponse createAuction(Long sellerId, CreateAuctionRequest request) {
        Auction auction = Auction.builder()
                .cardId(request.cardId())
                .sellerId(sellerId)
                .title(request.title())
                .description(request.description())
                .cardImageUrl(request.cardImageUrl())
                .startingPrice(request.startingPrice())
                .buyoutPrice(request.buyoutPrice())
                .status(AuctionStatus.PENDING)
                .startedAt(request.startedAt())
                .endedAt(request.endedAt())
                .build();
        return AuctionResponse.from(auctionRepository.save(auction));
    }

    public AuctionResponse cancelAuction(Long id, String cancelReason) {
        Auction auction = auctionRepository.findById(id)
                .orElseThrow(() -> new AuctionException(ErrorCode.AUCTION_NOT_FOUND));
        auction.cancel(cancelReason);
        return AuctionResponse.from(auction);
    }
}
