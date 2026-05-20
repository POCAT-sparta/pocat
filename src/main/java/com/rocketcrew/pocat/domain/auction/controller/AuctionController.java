package com.rocketcrew.pocat.domain.auction.controller;

import com.rocketcrew.pocat.domain.auction.dto.request.CreateAuctionRequest;
import com.rocketcrew.pocat.domain.auction.dto.request.UpdateAuctionRequest;
import com.rocketcrew.pocat.domain.auction.dto.response.AuctionResponse;
import com.rocketcrew.pocat.domain.auction.dto.response.CancelAuctionResponse;
import com.rocketcrew.pocat.domain.auction.dto.response.CreateAuctionResponse;
import com.rocketcrew.pocat.domain.auction.dto.response.SearchAuctionResponse;
import com.rocketcrew.pocat.domain.auction.dto.response.UpdateAuctionResponse;
import com.rocketcrew.pocat.domain.auction.enums.AuctionStatus;
import com.rocketcrew.pocat.domain.auction.service.AuctionCommandService;
import com.rocketcrew.pocat.domain.auction.service.AuctionQueryService;
import com.rocketcrew.pocat.domain.card.entity.enums.CardCategory;
import com.rocketcrew.pocat.domain.card.entity.enums.CardGrade;
import com.rocketcrew.pocat.global.dto.ApiResponseDto;
import com.rocketcrew.pocat.global.dto.PageResponseDto;
import com.rocketcrew.pocat.global.security.CustomUserDetails;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1")
public class AuctionController {

    private final AuctionQueryService auctionQueryService;
    private final AuctionCommandService auctionCommandService;

    // Search active auctions.
    @GetMapping("/auctions")
    public ResponseEntity<ApiResponseDto<PageResponseDto<SearchAuctionResponse>>> getAuctions(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String series,
            @RequestParam(required = false) String setName,
            @RequestParam(required = false) CardGrade grade,
            @RequestParam(required = false) CardCategory category,
            @PageableDefault(size = 20, sort = {"startedAt", "id"}, direction = Sort.Direction.DESC) Pageable pageable) {
        Page<SearchAuctionResponse> page = auctionQueryService.getAuctions(
                keyword, series, setName, grade, category, AuctionStatus.ACTIVE, pageable);
        PageResponseDto<SearchAuctionResponse> response = PageResponseDto.of(page, page.getContent());
        return ResponseEntity.ok(ApiResponseDto.success(HttpStatus.OK, response));
    }

    // Search auctions for administrators.
    @GetMapping("/admin/auctions")
    public ResponseEntity<ApiResponseDto<PageResponseDto<SearchAuctionResponse>>> getAdminAuctions(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String series,
            @RequestParam(required = false) String setName,
            @RequestParam(required = false) CardGrade grade,
            @RequestParam(required = false) CardCategory category,
            @RequestParam(required = false) AuctionStatus status,
            @PageableDefault(size = 20, sort = {"createdAt", "id"}, direction = Sort.Direction.DESC) Pageable pageable) {
        Page<SearchAuctionResponse> page = auctionQueryService.getAuctions(
                keyword, series, setName, grade, category, status, pageable);
        PageResponseDto<SearchAuctionResponse> response = PageResponseDto.of(page, page.getContent());
        return ResponseEntity.ok(ApiResponseDto.success(HttpStatus.OK, response));
    }

    // Search auctions owned by the authenticated seller.
    @GetMapping("/auctions/me")
    public ResponseEntity<ApiResponseDto<PageResponseDto<SearchAuctionResponse>>> getMyAuctions(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestParam(required = false) AuctionStatus status,
            @PageableDefault(size = 20, sort = {"startedAt", "id"}, direction = Sort.Direction.DESC) Pageable pageable) {
        Page<SearchAuctionResponse> page = auctionQueryService.getMyAuctions(userDetails.getUserId(), status, pageable);
        PageResponseDto<SearchAuctionResponse> response = PageResponseDto.of(page, page.getContent());
        return ResponseEntity.ok(ApiResponseDto.success(HttpStatus.OK, response));
    }

    // Get auction details.
    @GetMapping("/auctions/{auctionId}")
    public ResponseEntity<ApiResponseDto<AuctionResponse>> getAuction(@PathVariable Long auctionId) {
        AuctionResponse response = auctionQueryService.getAuction(auctionId);
        return ResponseEntity.ok(ApiResponseDto.success(HttpStatus.OK, response));
    }

    // Create an auction.
    @PostMapping("/auctions")
    public ResponseEntity<ApiResponseDto<CreateAuctionResponse>> createAuction(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @Valid @RequestBody CreateAuctionRequest request) {
        CreateAuctionResponse response = auctionCommandService.createAuction(userDetails.getUserId(), request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponseDto.success(HttpStatus.CREATED, response));
    }

    // Update an auction.
    @PatchMapping("/auctions/{auctionId}")
    public ResponseEntity<ApiResponseDto<UpdateAuctionResponse>> updateAuction(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long auctionId,
            @Valid @RequestBody UpdateAuctionRequest request) {
        UpdateAuctionResponse response = auctionCommandService.updateAuction(userDetails.getUserId(), auctionId, request);
        return ResponseEntity.ok(ApiResponseDto.success(HttpStatus.OK, response));
    }

    // Cancel an auction.
    @PatchMapping("/auctions/{auctionId}/cancel")
    public ResponseEntity<ApiResponseDto<CancelAuctionResponse>> cancelAuction(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long auctionId) {
        CancelAuctionResponse response = auctionCommandService.cancelAuction(userDetails.getUserId(), auctionId);
        return ResponseEntity.ok(ApiResponseDto.success(HttpStatus.OK, response));
    }
}
