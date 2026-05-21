package com.rocketcrew.pocat.domain.auction.controller;

import com.rocketcrew.pocat.domain.auction.dto.request.CreateAuctionRequest;
import com.rocketcrew.pocat.domain.auction.dto.request.InspectAuctionRequest;
import com.rocketcrew.pocat.domain.auction.dto.request.UpdateAuctionRequest;
import com.rocketcrew.pocat.domain.auction.dto.response.AuctionResponse;
import com.rocketcrew.pocat.domain.auction.dto.response.CancelAuctionResponse;
import com.rocketcrew.pocat.domain.auction.dto.response.CreateAuctionResponse;
import com.rocketcrew.pocat.domain.auction.dto.response.InspectAuctionResponse;
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
@RequestMapping("/api")
public class AuctionController {

    private final AuctionQueryService auctionQueryService;
    private final AuctionCommandService auctionCommandService;

    // 경매 목록 조회(유저)
    @GetMapping("/v1/auctions")
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

    // 경매 목록 조회(관리자)
    @GetMapping("/v1/admin/auctions")
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

    // 경매 목록 조회(판매자)
    @GetMapping("/v1/auctions/me")
    public ResponseEntity<ApiResponseDto<PageResponseDto<SearchAuctionResponse>>> getMyAuctions(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestParam(required = false) AuctionStatus status,
            @PageableDefault(size = 20, sort = {"startedAt", "id"}, direction = Sort.Direction.DESC) Pageable pageable) {
        Page<SearchAuctionResponse> page = auctionQueryService.getMyAuctions(userDetails.getUserId(), status, pageable);
        PageResponseDto<SearchAuctionResponse> response = PageResponseDto.of(page, page.getContent());
        return ResponseEntity.ok(ApiResponseDto.success(HttpStatus.OK, response));
    }

    // 경매 상세 조회
    @GetMapping("/v1/auctions/{auctionId}")
    public ResponseEntity<ApiResponseDto<AuctionResponse>> getAuction(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long auctionId) {
        Long userId = userDetails == null ? null : userDetails.getUserId();
        AuctionResponse response = auctionQueryService.getAuction(auctionId, userId);
        return ResponseEntity.ok(ApiResponseDto.success(HttpStatus.OK, response));
    }

    // 경매 등록(판매자)
    @PostMapping("/v1/auctions")
    public ResponseEntity<ApiResponseDto<CreateAuctionResponse>> createAuction(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @Valid @RequestBody CreateAuctionRequest request) {
        CreateAuctionResponse response = auctionCommandService.createAuction(userDetails.getUserId(), request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponseDto.success(HttpStatus.CREATED, response));
    }

    // 경매 수정
    @PatchMapping("/v1/auctions/{auctionId}")
    public ResponseEntity<ApiResponseDto<UpdateAuctionResponse>> updateAuction(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long auctionId,
            @Valid @RequestBody UpdateAuctionRequest request) {
        UpdateAuctionResponse response = auctionCommandService.updateAuction(userDetails.getUserId(), auctionId, request);
        return ResponseEntity.ok(ApiResponseDto.success(HttpStatus.OK, response));
    }

    // 경매 취소.
    @PatchMapping("/v1/auctions/{auctionId}/cancel")
    public ResponseEntity<ApiResponseDto<CancelAuctionResponse>> cancelAuction(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long auctionId) {
        CancelAuctionResponse response = auctionCommandService.cancelAuction(userDetails.getUserId(), auctionId);
        return ResponseEntity.ok(ApiResponseDto.success(HttpStatus.OK, response));
    }
    // 경매 검수
    @PatchMapping("/v1/admin/auctions/{auctionId}/inspection")
    public ResponseEntity<ApiResponseDto<InspectAuctionResponse>> inspectAuction(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long auctionId,
            @Valid @RequestBody InspectAuctionRequest request) {
        InspectAuctionResponse response = auctionCommandService.inspectAuction(userDetails.getUserId(), auctionId, request);
        return ResponseEntity.ok(ApiResponseDto.success(HttpStatus.OK, response));
    }
}
