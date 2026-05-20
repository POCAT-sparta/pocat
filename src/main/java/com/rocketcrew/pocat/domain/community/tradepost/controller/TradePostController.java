package com.rocketcrew.pocat.domain.community.tradepost.controller;

import com.rocketcrew.pocat.domain.community.tradepost.dto.request.CreateTradePostRequest;
import com.rocketcrew.pocat.domain.community.tradepost.dto.request.UpdateTradePostRequest;
import com.rocketcrew.pocat.domain.community.tradepost.dto.response.CreateTradePost;
import com.rocketcrew.pocat.domain.community.tradepost.dto.response.TradePostListResponse;
import com.rocketcrew.pocat.domain.community.tradepost.dto.response.TradePostResponse;
import com.rocketcrew.pocat.domain.community.tradepost.dto.response.UpdateTradePostResponse;
import com.rocketcrew.pocat.domain.community.tradepost.service.TradePostCommandService;
import com.rocketcrew.pocat.domain.community.tradepost.service.TradePostQueryService;
import com.rocketcrew.pocat.global.dto.ApiResponseDto;
import com.rocketcrew.pocat.global.dto.PageResponseDto;
import com.rocketcrew.pocat.global.security.CustomUserDetails;
import com.rocketcrew.pocat.global.util.HttpRequestUtils;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpServletRequest;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/posts/trade")
public class TradePostController {

    private final TradePostQueryService tradePostQueryService;
    private final TradePostCommandService tradePostCommandService;

    @GetMapping
    public ResponseEntity<ApiResponseDto<PageResponseDto<TradePostListResponse>>> getPosts(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long minPrice,
            @RequestParam(required = false) Long maxPrice,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        Page<TradePostListResponse> page = tradePostQueryService.getPosts(keyword, minPrice, maxPrice, pageable);
        List<TradePostListResponse> content = page.getContent();
        PageResponseDto<TradePostListResponse> pageResponse = PageResponseDto.of(page, content);
        return ResponseEntity.ok(ApiResponseDto.success(HttpStatus.OK, pageResponse));
    }

    @GetMapping("/me")
    public ResponseEntity<ApiResponseDto<PageResponseDto<TradePostListResponse>>> getPosts(
            @AuthenticationPrincipal CustomUserDetails customUserDetails,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {

        Page<TradePostListResponse> page = tradePostQueryService.getPostsByUserId(customUserDetails.getUserId(), pageable);
        List<TradePostListResponse> content = page.getContent();
        PageResponseDto<TradePostListResponse> pageResponse = PageResponseDto.of(page, content);
        return ResponseEntity.ok(ApiResponseDto.success(HttpStatus.OK, pageResponse));
    }

    @GetMapping("/{tradePostId}")
    public ResponseEntity<ApiResponseDto<TradePostResponse>> getPost(
            @PathVariable Long tradePostId,
            @AuthenticationPrincipal CustomUserDetails userDetails,
            HttpServletRequest request) {
        String clientIp = HttpRequestUtils.resolveClientIp(request);
        Long requesterId = userDetails != null ? userDetails.getUserId() : null;
        TradePostResponse response = tradePostQueryService.getPost(tradePostId, clientIp, requesterId);
        return ResponseEntity.ok(ApiResponseDto.success(HttpStatus.OK, response));
    }

    @PostMapping
    public ResponseEntity<ApiResponseDto<CreateTradePost>> createPost(
            @AuthenticationPrincipal CustomUserDetails customUserDetails,
            @RequestBody @Valid CreateTradePostRequest request) {
        CreateTradePost response = tradePostCommandService.createPost(customUserDetails.getUserId(), request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponseDto.success(HttpStatus.CREATED, response));
    }

    @PatchMapping("/{tradePostId}")
    public ResponseEntity<ApiResponseDto<UpdateTradePostResponse>> updatePost(
            @PathVariable Long tradePostId,
            @AuthenticationPrincipal CustomUserDetails customUserDetails,
            @RequestBody @Valid UpdateTradePostRequest request) {
        UpdateTradePostResponse response = tradePostCommandService.updatePost(tradePostId, customUserDetails.getUserId(), request);
        return ResponseEntity.ok(ApiResponseDto.success(HttpStatus.OK, response));
    }

    @DeleteMapping("/{tradePostId}")
    public ResponseEntity<ApiResponseDto<Void>> deletePost(
            @AuthenticationPrincipal CustomUserDetails customUserDetails,
            @PathVariable Long tradePostId) {
        tradePostCommandService.deletePost(tradePostId, customUserDetails.getUserId(), customUserDetails.getRole());
        return ResponseEntity.status(HttpStatus.NO_CONTENT)
                .body(ApiResponseDto.successWithNoContent());
    }
}
