package com.rocketcrew.pocat.domain.auction.repository;

import com.rocketcrew.pocat.domain.auction.dto.request.AuctionSearchCondition;
import com.rocketcrew.pocat.domain.auction.dto.response.AdminAuctionResponse;
import com.rocketcrew.pocat.domain.auction.dto.response.SearchAuctionResponse;
import com.rocketcrew.pocat.domain.auction.enums.AuctionStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface AuctionRepositoryCustom {

    Page<SearchAuctionResponse> searchAuctions(AuctionSearchCondition condition, Pageable pageable);

    Page<AdminAuctionResponse> searchAdminAuctions(AuctionSearchCondition condition, Pageable pageable);

    Page<SearchAuctionResponse> searchMyAuctions(Long sellerId, AuctionStatus status, Pageable pageable);
}
