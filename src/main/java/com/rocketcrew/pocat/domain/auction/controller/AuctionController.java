package com.rocketcrew.pocat.domain.auction.controller;

import com.rocketcrew.pocat.domain.auction.dto.request.CreateAuctionRequest;
import com.rocketcrew.pocat.domain.auction.dto.request.UpdateAuctionRequest;
import com.rocketcrew.pocat.domain.auction.dto.response.CancelAuctionResponse;
import com.rocketcrew.pocat.domain.auction.dto.response.CreateAuctionResponse;
import com.rocketcrew.pocat.domain.auction.dto.response.AuctionResponse;
import com.rocketcrew.pocat.domain.auction.dto.response.UpdateAuctionResponse;
import com.rocketcrew.pocat.domain.auction.service.AuctionCommandService;
import com.rocketcrew.pocat.domain.auction.service.AuctionQueryService;
import com.rocketcrew.pocat.global.dto.ApiResponseDto;
import com.rocketcrew.pocat.global.dto.PageResponseDto;
import com.rocketcrew.pocat.global.security.CustomUserDetails;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1")
public class AuctionController {

    private final AuctionQueryService auctionQueryService;
    private final AuctionCommandService auctionCommandService;

    // Todo : 경매 목록 조회 API
    @GetMapping("/auctions")
    public ResponseEntity<ApiResponseDto<PageResponseDto<AuctionResponse>>> getAuctions(
            @PageableDefault(size = 10) Pageable pageable) {
        Page<AuctionResponse> page = auctionQueryService.getAuctions(pageable);
        PageResponseDto<AuctionResponse> response = PageResponseDto.of(page, page.getContent());
        return ResponseEntity.ok(ApiResponseDto.success(HttpStatus.OK, response));
    }

    // Todo : 경매 상세 조회 API
    @GetMapping("/auctions/{auctionId}")
    public ResponseEntity<ApiResponseDto<AuctionResponse>> getAuction(@PathVariable Long auctionId) {
        AuctionResponse response = auctionQueryService.getAuction(auctionId);
        return ResponseEntity.ok(ApiResponseDto.success(HttpStatus.OK, response));
    }

    // 경매 등록 API (판매자)
    @PostMapping("/auctions")
    public ResponseEntity<ApiResponseDto<CreateAuctionResponse>> createAuction(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @Valid @RequestBody CreateAuctionRequest request) {
        CreateAuctionResponse response = auctionCommandService.createAuction(userDetails.getUserId(), request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponseDto.success(HttpStatus.CREATED, response));
    }

    // 경매 수정 API (판매자)
    @PatchMapping("/auctions/{auctionId}")
    public ResponseEntity<ApiResponseDto<UpdateAuctionResponse>> updateAuction(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long auctionId,
            @Valid @RequestBody UpdateAuctionRequest request) {
        UpdateAuctionResponse response = auctionCommandService.updateAuction(userDetails.getUserId(), auctionId, request);
        return ResponseEntity.ok(ApiResponseDto.success(HttpStatus.OK, response));
    }

    // 경매 취소 API (판매자)
    @PatchMapping("/auctions/{auctionId}/cancel")
    public ResponseEntity<ApiResponseDto<CancelAuctionResponse>> cancelAuction(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long auctionId) {
        CancelAuctionResponse response = auctionCommandService.cancelAuction(userDetails.getUserId(), auctionId);
        return ResponseEntity.ok(ApiResponseDto.success(HttpStatus.OK, response));
    }
}
