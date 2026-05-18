package com.rocketcrew.pocat.domain.community.tradepost.controller;

import com.rocketcrew.pocat.domain.community.tradepost.dto.request.CreateTradePostRequest;
import com.rocketcrew.pocat.domain.community.tradepost.dto.request.UpdateTradePostRequest;
import com.rocketcrew.pocat.domain.community.tradepost.dto.response.TradePostResponse;
import com.rocketcrew.pocat.domain.community.tradepost.service.TradePostCommandService;
import com.rocketcrew.pocat.domain.community.tradepost.service.TradePostQueryService;
import com.rocketcrew.pocat.global.dto.ApiResponseDto;
import com.rocketcrew.pocat.global.dto.PageResponseDto;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/trade-posts")
public class TradePostController {

    private final TradePostQueryService tradePostQueryService;
    private final TradePostCommandService tradePostCommandService;

    @GetMapping
    public ResponseEntity<ApiResponseDto<PageResponseDto<TradePostResponse>>> getPosts(
            @PageableDefault(size = 10, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        Page<TradePostResponse> page = tradePostQueryService.getPosts(pageable);
        List<TradePostResponse> content = page.getContent();
        PageResponseDto<TradePostResponse> pageResponse = PageResponseDto.of(page, content);
        return ResponseEntity.ok(ApiResponseDto.success(HttpStatus.OK, pageResponse));
    }

    @GetMapping("/{postId}")
    public ResponseEntity<ApiResponseDto<TradePostResponse>> getPost(@PathVariable Long postId) {
        TradePostResponse response = tradePostQueryService.getPost(postId);
        return ResponseEntity.ok(ApiResponseDto.success(HttpStatus.OK, response));
    }

    @PostMapping
    public ResponseEntity<ApiResponseDto<TradePostResponse>> createPost(
            @RequestParam Long userId,
            @RequestBody CreateTradePostRequest request) {
        TradePostResponse response = tradePostCommandService.createPost(userId, request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponseDto.success(HttpStatus.CREATED, response));
    }

    @PutMapping("/{postId}")
    public ResponseEntity<ApiResponseDto<TradePostResponse>> updatePost(
            @PathVariable Long postId,
            @RequestParam Long userId,
            @RequestBody UpdateTradePostRequest request) {
        TradePostResponse response = tradePostCommandService.updatePost(postId, userId, request);
        return ResponseEntity.ok(ApiResponseDto.success(HttpStatus.OK, response));
    }

    @DeleteMapping("/{postId}")
    public ResponseEntity<ApiResponseDto<Void>> deletePost(
            @PathVariable Long postId,
            @RequestParam Long userId) {
        tradePostCommandService.deletePost(postId, userId);
        return ResponseEntity.status(HttpStatus.NO_CONTENT)
                .body(ApiResponseDto.successWithNoContent());
    }
}
