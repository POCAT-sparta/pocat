package com.rocketcrew.pocat.domain.like.controller;

import com.rocketcrew.pocat.domain.like.dto.response.LikeResponse;
import com.rocketcrew.pocat.domain.like.service.LikeCommandService;
import com.rocketcrew.pocat.domain.like.service.LikeQueryService;
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
@RequestMapping("/api/v1/likes")
public class LikeController {

    private final LikeCommandService likeCommandService;
    private final LikeQueryService likeQueryService;

    @PostMapping("/auctions/{auctionId}")
    public ResponseEntity<ApiResponseDto<LikeResponse>> toggleLike(
            @PathVariable Long auctionId,
            @RequestParam Long userId) {
        LikeResponse response = likeCommandService.toggleLike(userId, auctionId);
        return ResponseEntity.ok(ApiResponseDto.success(HttpStatus.OK, response));
    }

    @GetMapping
    public ResponseEntity<ApiResponseDto<PageResponseDto<LikeResponse>>> getMyLikes(
            @RequestParam Long userId,
            @PageableDefault(size = 10) Pageable pageable) {
        Page<LikeResponse> page = likeQueryService.getMyLikes(userId, pageable);
        PageResponseDto<LikeResponse> response = PageResponseDto.of(page, page.getContent());
        return ResponseEntity.ok(ApiResponseDto.success(HttpStatus.OK, response));
    }
}
