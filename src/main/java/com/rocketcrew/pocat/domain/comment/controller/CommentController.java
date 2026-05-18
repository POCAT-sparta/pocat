package com.rocketcrew.pocat.domain.comment.controller;

import com.rocketcrew.pocat.domain.comment.dto.request.CreateCommentRequest;
import com.rocketcrew.pocat.domain.comment.dto.request.UpdateCommentRequest;
import com.rocketcrew.pocat.domain.comment.dto.response.CommentResponse;
import com.rocketcrew.pocat.domain.comment.service.CommentCommandService;
import com.rocketcrew.pocat.domain.comment.service.CommentQueryService;
import com.rocketcrew.pocat.global.dto.ApiResponseDto;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/comments")
public class CommentController {

    private final CommentQueryService commentQueryService;
    private final CommentCommandService commentCommandService;

    @GetMapping
    public ResponseEntity<ApiResponseDto<List<CommentResponse>>> getCommentsByPost(
            @RequestParam Long postId) {
        List<CommentResponse> responses = commentQueryService.getCommentsByPost(postId);
        return ResponseEntity.ok(ApiResponseDto.success(HttpStatus.OK, responses));
    }

    @PostMapping
    public ResponseEntity<ApiResponseDto<CommentResponse>> createComment(
            @RequestParam Long userId,
            @RequestBody CreateCommentRequest request) {
        CommentResponse response = commentCommandService.createComment(userId, request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponseDto.success(HttpStatus.CREATED, response));
    }

    @PutMapping("/{commentId}")
    public ResponseEntity<ApiResponseDto<CommentResponse>> updateComment(
            @PathVariable Long commentId,
            @RequestParam Long userId,
            @RequestBody UpdateCommentRequest request) {
        CommentResponse response = commentCommandService.updateComment(commentId, userId, request);
        return ResponseEntity.ok(ApiResponseDto.success(HttpStatus.OK, response));
    }

    @DeleteMapping("/{commentId}")
    public ResponseEntity<ApiResponseDto<Void>> deleteComment(
            @PathVariable Long commentId,
            @RequestParam Long userId) {
        commentCommandService.deleteComment(commentId, userId);
        return ResponseEntity.status(HttpStatus.NO_CONTENT)
                .body(ApiResponseDto.successWithNoContent());
    }
}
