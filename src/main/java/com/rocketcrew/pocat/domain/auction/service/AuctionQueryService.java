package com.rocketcrew.pocat.domain.auction.service;

import com.rocketcrew.pocat.domain.auction.dto.request.AuctionSearchCondition;
import com.rocketcrew.pocat.domain.auction.dto.response.AuctionResponse;
import com.rocketcrew.pocat.domain.auction.dto.response.SearchAuctionResponse;
import com.rocketcrew.pocat.domain.auction.entity.Auction;
import com.rocketcrew.pocat.domain.auction.enums.AuctionStatus;
import com.rocketcrew.pocat.domain.auction.repository.AuctionRepository;
import com.rocketcrew.pocat.domain.card.entity.enums.CardCategory;
import com.rocketcrew.pocat.domain.card.entity.enums.CardGrade;
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

    public Page<SearchAuctionResponse> getAuctions(
            String keyword,
            String series,
            String setName,
            CardGrade grade,
            CardCategory category,
            AuctionStatus status,
            Pageable pageable
    ) {
        AuctionStatus targetStatus = status == null ? AuctionStatus.PENDING : status;
        AuctionSearchCondition condition = new AuctionSearchCondition(
                keyword, series, setName, grade, category, targetStatus);
        return auctionRepository.searchAuctions(condition, pageable);
    }

    public Page<SearchAuctionResponse> getMyAuctions(Long sellerId, AuctionStatus status, Pageable pageable) {
        return auctionRepository.searchMyAuctions(sellerId, status, pageable);
    }

    public AuctionResponse getAuction(Long id) {
        Auction auction = auctionRepository.findById(id)
                .orElseThrow(() -> new AuctionException(ErrorCode.AUCTION_NOT_FOUND));
        return AuctionResponse.from(auction);
    }
}
