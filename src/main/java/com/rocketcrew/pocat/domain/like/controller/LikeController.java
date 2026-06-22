package com.rocketcrew.pocat.domain.like.controller;

import io.swagger.v3.oas.annotations.tags.Tag;
import com.rocketcrew.pocat.domain.like.dto.request.ToggleLikeRequest;
import com.rocketcrew.pocat.domain.like.dto.response.LikeResponse;
import com.rocketcrew.pocat.domain.like.dto.response.ToggleLikeResponse;
import com.rocketcrew.pocat.domain.like.service.LikeCommandService;
import com.rocketcrew.pocat.domain.like.service.LikeQueryService;
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
import org.springframework.web.bind.annotation.*;

@Tag(name = "좋아요", description = "게시글 좋아요 토글")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api")
public class LikeController {

    private final LikeCommandService likeCommandService;
    private final LikeQueryService likeQueryService;

    @PostMapping("/v1/likes")
    public ResponseEntity<ApiResponseDto<ToggleLikeResponse>> toggleLike(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @Valid @RequestBody ToggleLikeRequest request) {
        ToggleLikeResponse response = likeCommandService.toggleLike(userDetails.getUserId(), request.auctionId());
        return ResponseEntity.ok(ApiResponseDto.success(HttpStatus.OK, response));
    }

    @GetMapping("/v1/likes/me")
    public ResponseEntity<ApiResponseDto<PageResponseDto<LikeResponse>>> getMyLikes(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PageableDefault(size = 10) Pageable pageable) {
        Page<LikeResponse> page = likeQueryService.getMyLikes(userDetails.getUserId(), pageable);
        PageResponseDto<LikeResponse> response = PageResponseDto.of(page, page.getContent());
        return ResponseEntity.ok(ApiResponseDto.success(HttpStatus.OK, response));
    }
}
