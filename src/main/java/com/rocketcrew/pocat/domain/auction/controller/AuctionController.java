package com.rocketcrew.pocat.domain.auction.controller;

import com.rocketcrew.pocat.domain.auction.dto.request.CreateAuctionRequest;
import com.rocketcrew.pocat.domain.auction.dto.response.AuctionResponse;
import com.rocketcrew.pocat.domain.auction.service.AuctionCommandService;
import com.rocketcrew.pocat.domain.auction.service.AuctionQueryService;
import com.rocketcrew.pocat.global.dto.ApiResponseDto;
import com.rocketcrew.pocat.global.dto.PageResponseDto;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;


@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/auctions")
public class AuctionController {

    private final AuctionQueryService auctionQueryService;
    private final AuctionCommandService auctionCommandService;

    // 경매 목록 조회(유저)
    // Todo : 카드 이름 키워드 검색 및 시리즈, 확장팩명, 카드 번호, 등급 받아서 필터링 검색 구현 필요,
    //  목록 조회용 response로 수정 필요. 카드 도메인에서 조회해서 카드 정보 필요
    @GetMapping
    public ResponseEntity<ApiResponseDto<PageResponseDto<AuctionResponse>>> getAuctions(
            @PageableDefault(size = 10) Pageable pageable) {
        Page<AuctionResponse> page = auctionQueryService.getAuctions(pageable);
        PageResponseDto<AuctionResponse> response = PageResponseDto.of(page, page.getContent());
        return ResponseEntity.ok(ApiResponseDto.success(HttpStatus.OK, response));
    }

    // 경매 상세 조회
    // Todo : 카드 상세조회 response dto로 만들어서 구현 필요. 카드 도메인에서 조회해서 카드 정보 필요
    @GetMapping("/{auctionId}")
    public ResponseEntity<ApiResponseDto<AuctionResponse>> getAuction(@PathVariable Long auctionId) {
        AuctionResponse response = auctionQueryService.getAuction(auctionId);
        return ResponseEntity.ok(ApiResponseDto.success(HttpStatus.OK, response));
    }

    // Todo : request, response 변경 필요.
    @PostMapping
    public ResponseEntity<ApiResponseDto<AuctionResponse>> createAuction(
            @RequestParam Long sellerId,
            @RequestBody CreateAuctionRequest request) {
        AuctionResponse response = auctionCommandService.createAuction(sellerId, request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponseDto.success(HttpStatus.CREATED, response));
    }

    // Todo : request, response 변경 필요.
    @PutMapping("/{auctionId}/cancel")
    public ResponseEntity<ApiResponseDto<AuctionResponse>> cancelAuction(
            @PathVariable Long auctionId,
            @RequestParam String cancelReason) {
        AuctionResponse response = auctionCommandService.cancelAuction(auctionId, cancelReason);
        return ResponseEntity.ok(ApiResponseDto.success(HttpStatus.OK, response));
    }
}
