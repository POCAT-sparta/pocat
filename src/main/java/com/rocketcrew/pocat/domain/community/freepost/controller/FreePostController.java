package com.rocketcrew.pocat.domain.community.freepost.controller;

import com.rocketcrew.pocat.domain.community.freepost.dto.request.CreateFreePostRequest;
import com.rocketcrew.pocat.domain.community.freepost.dto.request.UpdateFreePostRequest;
import com.rocketcrew.pocat.domain.community.freepost.dto.response.FreePostResponse;
import com.rocketcrew.pocat.domain.community.freepost.service.FreePostCommandService;
import com.rocketcrew.pocat.domain.community.freepost.service.FreePostQueryService;
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
@RequestMapping("/api/v1/posts/free")
public class FreePostController {

    private final FreePostQueryService freePostQueryService;
    private final FreePostCommandService freePostCommandService;

    @GetMapping
    public ResponseEntity<ApiResponseDto<PageResponseDto<FreePostResponse>>> getPosts(
            @PageableDefault(size = 10, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        Page<FreePostResponse> page = freePostQueryService.getPosts(pageable);
        List<FreePostResponse> content = page.getContent();
        PageResponseDto<FreePostResponse> pageResponse = PageResponseDto.of(page, content);
        return ResponseEntity.ok(ApiResponseDto.success(HttpStatus.OK, pageResponse));
    }

    @GetMapping("/me")
    public ResponseEntity<ApiResponseDto<PageResponseDto<FreePostResponse>>> getMyPosts(
            @RequestParam Long userId,
            @PageableDefault(size = 10, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        Page<FreePostResponse> page = freePostQueryService.getMyPosts(userId, pageable);
        List<FreePostResponse> content = page.getContent();
        PageResponseDto<FreePostResponse> pageResponse = PageResponseDto.of(page, content);
        return ResponseEntity.ok(ApiResponseDto.success(HttpStatus.OK, pageResponse));
    }

    @GetMapping("/{freePostId}")
    public ResponseEntity<ApiResponseDto<FreePostResponse>> getPost(@PathVariable Long freePostId) {
        freePostCommandService.incrementViewCount(freePostId);
        FreePostResponse response = freePostQueryService.getPost(freePostId);
        return ResponseEntity.ok(ApiResponseDto.success(HttpStatus.OK, response));
    }

    @PostMapping
    public ResponseEntity<ApiResponseDto<FreePostResponse>> createPost(
            @RequestParam Long userId,
            @RequestBody CreateFreePostRequest request) {
        FreePostResponse response = freePostCommandService.createPost(userId, request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponseDto.success(HttpStatus.CREATED, response));
    }

    @PatchMapping("/{freePostId}")
    public ResponseEntity<ApiResponseDto<FreePostResponse>> updatePost(
            @PathVariable Long freePostId,
            @RequestParam Long userId,
            @RequestBody UpdateFreePostRequest request) {
        FreePostResponse response = freePostCommandService.updatePost(freePostId, userId, request);
        return ResponseEntity.ok(ApiResponseDto.success(HttpStatus.OK, response));
    }

    @DeleteMapping("/{freePostId}")
    public ResponseEntity<ApiResponseDto<Void>> deletePost(
            @PathVariable Long freePostId,
            @RequestParam Long userId) {
        freePostCommandService.deletePost(freePostId, userId);
        return ResponseEntity.status(HttpStatus.NO_CONTENT)
                .body(ApiResponseDto.successWithNoContent());
    }
}
