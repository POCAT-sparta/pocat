package com.rocketcrew.pocat.domain.comment.service;

import com.rocketcrew.pocat.domain.comment.dto.request.CreateCommentRequest;
import com.rocketcrew.pocat.domain.comment.dto.request.UpdateCommentRequest;
import com.rocketcrew.pocat.domain.comment.dto.response.CommentResponse;
import com.rocketcrew.pocat.domain.comment.entity.Comment;
import com.rocketcrew.pocat.domain.comment.repository.CommentRepository;
import com.rocketcrew.pocat.global.exception.common.ErrorCode;
import com.rocketcrew.pocat.global.exception.domain.CommentException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class CommentCommandService {

    private final CommentRepository commentRepository;

    public CommentResponse createComment(Long userId, CreateCommentRequest request) {
        Comment comment = Comment.builder()
                .userId(userId)
                .postId(request.postId())
                .parentId(request.parentId())
                .content(request.content())
                .build();
        return CommentResponse.from(commentRepository.save(comment));
    }

    public CommentResponse updateComment(Long id, Long userId, UpdateCommentRequest request) {
        Comment comment = commentRepository.findById(id)
                .orElseThrow(() -> new CommentException(ErrorCode.COMMENT_NOT_FOUND));
        if (!comment.getUserId().equals(userId)) {
            throw new CommentException(ErrorCode.USER_FORBIDDEN);
        }
        comment.update(request.content());
        return CommentResponse.from(comment);
    }

    public void deleteComment(Long id, Long userId) {
        Comment comment = commentRepository.findById(id)
                .orElseThrow(() -> new CommentException(ErrorCode.COMMENT_NOT_FOUND));
        if (!comment.getUserId().equals(userId)) {
            throw new CommentException(ErrorCode.USER_FORBIDDEN);
        }
        commentRepository.delete(comment);
    }
}
