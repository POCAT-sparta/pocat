package com.rocketcrew.pocat.domain.bid.controller;

import com.rocketcrew.pocat.domain.bid.dto.request.CreateBidRequest;
import com.rocketcrew.pocat.domain.bid.dto.response.AuctionBidResponse;
import com.rocketcrew.pocat.domain.bid.service.AuctionBidCommandService;
import com.rocketcrew.pocat.domain.bid.service.AuctionBidQueryService;
import com.rocketcrew.pocat.global.dto.ApiResponseDto;
import com.rocketcrew.pocat.global.security.CustomUserDetails;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1")
public class AuctionBidController {

    private final AuctionBidQueryService auctionBidQueryService;
    private final AuctionBidCommandService auctionBidCommandService;

    // 특정 경매 입찰 목록 조회.
    @GetMapping("/auctions/{auctionId}/bids")
    public ResponseEntity<ApiResponseDto<List<AuctionBidResponse>>> getBidsByAuction(
            @PathVariable Long auctionId) {
        List<AuctionBidResponse> response = auctionBidQueryService.getBidsByAuction(auctionId);
        return ResponseEntity.ok(ApiResponseDto.success(HttpStatus.OK, response));
    }


    // 입찰
    // Todo : request 및 내부 로직 수정 필요
    @PostMapping("/auctions/{auctionId}/bids")
    public ResponseEntity<ApiResponseDto<AuctionBidResponse>> createBid(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestBody CreateBidRequest request) {
        AuctionBidResponse response = auctionBidCommandService.createBid(userDetails.getUserId(), request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponseDto.success(HttpStatus.CREATED, response));
    }
}
