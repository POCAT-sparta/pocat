package com.rocketcrew.pocat.domain.bid.controller;

import com.rocketcrew.pocat.domain.bid.dto.request.CreateBidRequest;
import com.rocketcrew.pocat.domain.bid.dto.response.AuctionBidResponse;
import com.rocketcrew.pocat.domain.bid.service.AuctionBidService;
import com.rocketcrew.pocat.global.dto.ApiResponseDto;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
public class AuctionBidController {

    private final AuctionBidService auctionBidService;

    @GetMapping("/api/v1/auctions/{auctionId}/bids")
    public ResponseEntity<ApiResponseDto<List<AuctionBidResponse>>> getBidsByAuction(
            @PathVariable Long auctionId) {
        List<AuctionBidResponse> response = auctionBidService.getBidsByAuction(auctionId);
        return ResponseEntity.ok(ApiResponseDto.success(HttpStatus.OK, response));
    }

    @GetMapping("/api/v1/bids/{bidId}")
    public ResponseEntity<ApiResponseDto<AuctionBidResponse>> getBid(@PathVariable Long bidId) {
        AuctionBidResponse response = auctionBidService.getBid(bidId);
        return ResponseEntity.ok(ApiResponseDto.success(HttpStatus.OK, response));
    }

    @PostMapping("/api/v1/bids")
    public ResponseEntity<ApiResponseDto<AuctionBidResponse>> createBid(
            @RequestParam Long userId,
            @RequestBody CreateBidRequest request) {
        AuctionBidResponse response = auctionBidService.createBid(userId, request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponseDto.success(HttpStatus.CREATED, response));
    }
}
