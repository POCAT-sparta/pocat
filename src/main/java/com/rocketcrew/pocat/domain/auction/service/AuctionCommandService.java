package com.rocketcrew.pocat.domain.auction.service;

import com.rocketcrew.pocat.domain.auction.dto.request.CreateAuctionRequest;
import com.rocketcrew.pocat.domain.auction.dto.request.UpdateAuctionRequest;
import com.rocketcrew.pocat.domain.auction.dto.response.CancelAuctionResponse;
import com.rocketcrew.pocat.domain.auction.dto.response.CreateAuctionResponse;
import com.rocketcrew.pocat.domain.auction.dto.response.UpdateAuctionResponse;
import com.rocketcrew.pocat.domain.auction.entity.Auction;
import com.rocketcrew.pocat.domain.auction.enums.AuctionStatus;
import com.rocketcrew.pocat.domain.auction.repository.AuctionRepository;
import com.rocketcrew.pocat.global.exception.common.ErrorCode;
import com.rocketcrew.pocat.global.exception.domain.AuctionException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class AuctionCommandService {

    private final AuctionRepository auctionRepository;

    public CreateAuctionResponse createAuction(Long sellerId, CreateAuctionRequest request) {
        Auction auction = Auction.builder()
                .cardId(request.cardId())
                .sellerId(sellerId)
                .title(request.title())
                .description(request.description())
                .startingPrice(request.startingPrice())
                .buyoutPrice(request.buyoutPrice())
                .status(AuctionStatus.PENDING)
                .build();
        return CreateAuctionResponse.from(auctionRepository.save(auction));
    }

    public UpdateAuctionResponse updateAuction(Long sellerId, Long id, UpdateAuctionRequest request) {
        Auction auction = auctionRepository.findById(id)
                .orElseThrow(() -> new AuctionException(ErrorCode.AUCTION_NOT_FOUND));
        validateSeller(auction, sellerId);
        validatePending(auction);
        auction.update(request.title(), request.description(), request.startingPrice(), request.buyoutPrice());
        return UpdateAuctionResponse.from(auction);
    }

    public CancelAuctionResponse cancelAuction(Long sellerId, Long id) {
        Auction auction = auctionRepository.findById(id)
                .orElseThrow(() -> new AuctionException(ErrorCode.AUCTION_NOT_FOUND));
        validateSeller(auction, sellerId);
        auction.cancel(null);
        return CancelAuctionResponse.from(auction);
    }

    private void validateSeller(Auction auction, Long sellerId) {
        if (!auction.getSellerId().equals(sellerId)) {
            throw new AuctionException(ErrorCode.USER_FORBIDDEN);
        }
    }

    private void validatePending(Auction auction) {
        if (auction.getStatus() != AuctionStatus.PENDING) {
            throw new AuctionException(ErrorCode.AUCTION_NOT_PENDING);
        }
    }
}
