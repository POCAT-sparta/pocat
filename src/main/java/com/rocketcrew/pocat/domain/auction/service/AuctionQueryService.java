package com.rocketcrew.pocat.domain.auction.service;

import com.rocketcrew.pocat.domain.auction.dto.response.AuctionResponse;
import com.rocketcrew.pocat.domain.auction.entity.Auction;
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
@Transactional(readOnly = true)
public class AuctionQueryService {

    private final AuctionRepository auctionRepository;

    public Page<AuctionResponse> getAuctions(Pageable pageable) {
        return auctionRepository.findAll(pageable)
                .map(AuctionResponse::from);
    }

    // Todo : 경매 상세 조회 response dto에 경매 필드에 없는 카드 상세 정보 추가 필요.
    public AuctionResponse getAuction(Long id) {
        Auction auction = auctionRepository.findById(id)
                .orElseThrow(() -> new AuctionException(ErrorCode.AUCTION_NOT_FOUND));
        return AuctionResponse.from(auction);
    }
}
