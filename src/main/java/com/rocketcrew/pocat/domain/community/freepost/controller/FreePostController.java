package com.rocketcrew.pocat.domain.community.freepost.controller;

import io.swagger.v3.oas.annotations.tags.Tag;
import com.rocketcrew.pocat.domain.community.freepost.dto.request.CreateFreePostRequest;
import com.rocketcrew.pocat.domain.community.freepost.dto.request.UpdateFreePostRequest;
import com.rocketcrew.pocat.domain.community.freepost.dto.response.FreePostResponse;
import com.rocketcrew.pocat.domain.community.freepost.service.FreePostCommandService;
import com.rocketcrew.pocat.domain.community.freepost.service.FreePostQueryService;
import com.rocketcrew.pocat.domain.community.freepost.service.FreePostRankingService;
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
import com.rocketcrew.pocat.global.util.HttpRequestUtils;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "자유게시판", description = "자유 게시글 CRUD")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api")
public class FreePostController {

    private final FreePostQueryService freePostQueryService;
    private final FreePostCommandService freePostCommandService;
    private final FreePostRankingService freePostRankingService;

    @GetMapping("/v1/posts/free")
    public ResponseEntity<ApiResponseDto<PageResponseDto<FreePostResponse>>> getPosts(
            @RequestParam(required = false) String keyword,
            @PageableDefault(size = 10, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        Page<FreePostResponse> page = freePostQueryService.getPosts(keyword, pageable);
        List<FreePostResponse> content = page.getContent();
        PageResponseDto<FreePostResponse> pageResponse = PageResponseDto.of(page, content);
        return ResponseEntity.ok(ApiResponseDto.success(HttpStatus.OK, pageResponse));
    }

    @GetMapping("/v1/posts/free/me")
    public ResponseEntity<ApiResponseDto<PageResponseDto<FreePostResponse>>> getMyPosts(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PageableDefault(size = 10, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        Page<FreePostResponse> page = freePostQueryService.getMyPosts(userDetails.getUserId(), pageable);
        List<FreePostResponse> content = page.getContent();
        PageResponseDto<FreePostResponse> pageResponse = PageResponseDto.of(page, content);
        return ResponseEntity.ok(ApiResponseDto.success(HttpStatus.OK, pageResponse));
    }

    @GetMapping("/v1/posts/free/{freePostId}")
    public ResponseEntity<ApiResponseDto<FreePostResponse>> getPost(
            @PathVariable Long freePostId,
            @AuthenticationPrincipal CustomUserDetails userDetails,
            HttpServletRequest request) {
        String clientIp = HttpRequestUtils.resolveClientIp(request);
        Long requesterId = userDetails != null ? userDetails.getUserId() : null;
        FreePostResponse response = freePostQueryService.getPost(freePostId, clientIp, requesterId);
        return ResponseEntity.ok(ApiResponseDto.success(HttpStatus.OK, response));
    }

    @GetMapping("/v1/posts/free/popular")
    public ResponseEntity<ApiResponseDto<List<FreePostResponse>>> getPopularPosts(
            @RequestParam(defaultValue = "20") int size) {
        if (size < 1 || size > 100) {
            size = 20;
        }
        List<FreePostResponse> response = freePostRankingService.getPopular(size);
        return ResponseEntity.ok(ApiResponseDto.success(HttpStatus.OK, response));
    }

    @PostMapping("/v1/posts/free")
    public ResponseEntity<ApiResponseDto<FreePostResponse>> createPost(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @Valid @RequestBody CreateFreePostRequest request) {
        FreePostResponse response = freePostCommandService.createPost(userDetails.getUserId(), request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponseDto.success(HttpStatus.CREATED, response));
    }

    @PatchMapping("/v1/posts/free/{freePostId}")
    public ResponseEntity<ApiResponseDto<FreePostResponse>> updatePost(
            @PathVariable Long freePostId,
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestBody UpdateFreePostRequest request) {
        FreePostResponse response = freePostCommandService.updatePost(freePostId, userDetails.getUserId(), request);
        return ResponseEntity.ok(ApiResponseDto.success(HttpStatus.OK, response));
    }

    @DeleteMapping("/v1/posts/free/{freePostId}")
    public ResponseEntity<ApiResponseDto<Void>> deletePost(
            @PathVariable Long freePostId,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        freePostCommandService.deletePost(freePostId, userDetails.getUserId());
        return ResponseEntity.ok(ApiResponseDto.successWithNoContent());
    }
}
