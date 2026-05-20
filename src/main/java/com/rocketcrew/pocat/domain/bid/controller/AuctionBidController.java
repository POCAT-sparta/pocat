package com.rocketcrew.pocat.domain.bid.controller;

import com.rocketcrew.pocat.domain.bid.dto.request.CreateBidRequest;
import com.rocketcrew.pocat.domain.bid.dto.response.AuctionBidHistoryResponse;
import com.rocketcrew.pocat.domain.bid.dto.response.CreateAuctionBidResponse;
import com.rocketcrew.pocat.domain.bid.dto.response.MyBidResponse;
import com.rocketcrew.pocat.domain.bid.enums.BidStatus;
import com.rocketcrew.pocat.domain.bid.service.AuctionBidCommandService;
import com.rocketcrew.pocat.domain.bid.service.AuctionBidQueryService;
import com.rocketcrew.pocat.global.dto.ApiResponseDto;
import com.rocketcrew.pocat.global.dto.PageResponseDto;
import com.rocketcrew.pocat.global.security.CustomUserDetails;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1")
public class AuctionBidController {

    private final AuctionBidQueryService auctionBidQueryService;
    private final AuctionBidCommandService auctionBidCommandService;

    // Get bid history for an auction.
    @GetMapping("/auctions/{auctionId}/bids")
    public ResponseEntity<ApiResponseDto<PageResponseDto<AuctionBidHistoryResponse>>> getBidsByAuction(
            @PathVariable Long auctionId,
            @PageableDefault(size = 20) Pageable pageable) {
        Page<AuctionBidHistoryResponse> page = auctionBidQueryService.getBidHistoryByAuction(auctionId, pageable);
        PageResponseDto<AuctionBidHistoryResponse> response = PageResponseDto.of(page, page.getContent());
        return ResponseEntity.ok(ApiResponseDto.success(HttpStatus.OK, response));
    }

    // Search bids placed by the authenticated user.
    @GetMapping("/bids/me")
    public ResponseEntity<ApiResponseDto<PageResponseDto<MyBidResponse>>> getMyBids(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestParam(required = false) BidStatus status,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        Page<MyBidResponse> page = auctionBidQueryService.getMyBids(userDetails.getUserId(), status, pageable);
        PageResponseDto<MyBidResponse> response = PageResponseDto.of(page, page.getContent());
        return ResponseEntity.ok(ApiResponseDto.success(HttpStatus.OK, response));
    }

    // Place a bid on an auction.
    // Todo : 입찰 생성 내부 로직 구현 미완료
    @PostMapping("/auctions/{auctionId}/bids")
    public ResponseEntity<ApiResponseDto<CreateAuctionBidResponse>> createBid(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long auctionId,
            @RequestBody CreateBidRequest request) {
        CreateAuctionBidResponse response = auctionBidCommandService.createBid(userDetails.getUserId(), auctionId, request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponseDto.success(HttpStatus.CREATED, response));
    }
}
