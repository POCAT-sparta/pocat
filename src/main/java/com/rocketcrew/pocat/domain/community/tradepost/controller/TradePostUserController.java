package com.rocketcrew.pocat.domain.community.tradepost.controller;

import com.rocketcrew.pocat.domain.community.tradepost.dto.response.TradePostListResponse;
import com.rocketcrew.pocat.domain.community.tradepost.service.TradePostQueryService;
import com.rocketcrew.pocat.global.dto.ApiResponseDto;
import com.rocketcrew.pocat.global.dto.PageResponseDto;
import com.rocketcrew.pocat.global.security.CustomUserDetails;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/users/me/posts/trade")
public class TradePostUserController {

    private final TradePostQueryService tradePostQueryService;

    @GetMapping
    public ResponseEntity<ApiResponseDto<PageResponseDto<TradePostListResponse>>> getPosts(
            @AuthenticationPrincipal CustomUserDetails customUserDetails,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {

        Page<TradePostListResponse> page = tradePostQueryService.getPostsByUserId(customUserDetails.getUserId(), pageable);
        List<TradePostListResponse> content = page.getContent();
        PageResponseDto<TradePostListResponse> pageResponse = PageResponseDto.of(page, content);
        return ResponseEntity.ok(ApiResponseDto.success(HttpStatus.OK, pageResponse));
    }
}
