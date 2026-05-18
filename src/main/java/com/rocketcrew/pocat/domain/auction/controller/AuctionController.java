package com.rocketcrew.pocat.domain.auction.controller;

import com.rocketcrew.pocat.domain.auction.dto.request.CreateAuctionRequest;
import com.rocketcrew.pocat.domain.auction.dto.response.AuctionResponse;
import com.rocketcrew.pocat.domain.auction.service.AuctionService;
import com.rocketcrew.pocat.global.dto.ApiResponseDto;
import com.rocketcrew.pocat.global.dto.PageResponseDto;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/auctions")
public class AuctionController {

    private final AuctionService auctionService;

    @GetMapping
    public ResponseEntity<ApiResponseDto<PageResponseDto<AuctionResponse>>> getAuctions(
            @PageableDefault(size = 10) Pageable pageable) {
        Page<AuctionResponse> page = auctionService.getAuctions(pageable);
        PageResponseDto<AuctionResponse> response = PageResponseDto.of(page, page.getContent());
        return ResponseEntity.ok(ApiResponseDto.success(HttpStatus.OK, response));
    }

    @GetMapping("/{auctionId}")
    public ResponseEntity<ApiResponseDto<AuctionResponse>> getAuction(@PathVariable Long auctionId) {
        AuctionResponse response = auctionService.getAuction(auctionId);
        return ResponseEntity.ok(ApiResponseDto.success(HttpStatus.OK, response));
    }

    @PostMapping
    public ResponseEntity<ApiResponseDto<AuctionResponse>> createAuction(
            @RequestParam Long sellerId,
            @RequestBody CreateAuctionRequest request) {
        AuctionResponse response = auctionService.createAuction(sellerId, request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponseDto.success(HttpStatus.CREATED, response));
    }

    @PutMapping("/{auctionId}/cancel")
    public ResponseEntity<ApiResponseDto<AuctionResponse>> cancelAuction(
            @PathVariable Long auctionId,
            @RequestParam String cancelReason) {
        AuctionResponse response = auctionService.cancelAuction(auctionId, cancelReason);
        return ResponseEntity.ok(ApiResponseDto.success(HttpStatus.OK, response));
    }
}
