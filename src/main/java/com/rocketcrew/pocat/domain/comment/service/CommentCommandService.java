package com.rocketcrew.pocat.domain.comment.service;

import com.rocketcrew.pocat.domain.comment.dto.request.CreateCommentRequest;
import com.rocketcrew.pocat.domain.comment.dto.request.UpdateCommentRequest;
import com.rocketcrew.pocat.domain.comment.dto.response.CommentResponse;
import com.rocketcrew.pocat.domain.comment.entity.Comment;
import com.rocketcrew.pocat.domain.comment.repository.CommentRepository;
import com.rocketcrew.pocat.domain.community.freepost.repository.FreePostRepository;
import com.rocketcrew.pocat.domain.community.freepost.service.FreePostCommentCountService;
import com.rocketcrew.pocat.global.exception.common.ErrorCode;
import com.rocketcrew.pocat.global.exception.domain.CommentException;
import com.rocketcrew.pocat.global.exception.domain.FreePostException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class CommentCommandService {

    private final CommentRepository commentRepository;
    private final FreePostRepository freePostRepository;
    private final FreePostCommentCountService freePostCommentCountService;

    public CommentResponse createComment(Long userId, CreateCommentRequest request) {
        if (!freePostRepository.existsById(request.freePostId())) {
            throw new FreePostException(ErrorCode.FREE_POST_NOT_FOUND);
        }
        if (request.parentId() != null) {
            Comment parentComment = commentRepository.findById(request.parentId())
                    .orElseThrow(() -> new CommentException(ErrorCode.INVALID_PARENT_COMMENT));
            if (!parentComment.getFreePostId().equals(request.freePostId())) {
                throw new CommentException(ErrorCode.INVALID_PARENT_COMMENT);
            }
            if (parentComment.getParentId() != null) {
                throw new CommentException(ErrorCode.INVALID_PARENT_COMMENT);
            }
        }
        Comment comment = Comment.builder()
                .userId(userId)
                .freePostId(request.freePostId())
                .parentId(request.parentId())
                .content(request.content())
                .build();
        CommentResponse response = CommentResponse.from(commentRepository.save(comment));
        freePostCommentCountService.increment(request.freePostId());
        return response;
    }

    public CommentResponse updateComment(Long id, Long userId, UpdateCommentRequest request) {
        Comment comment = findCommentAndVerifyOwner(id, userId);
        comment.update(request.content());
        return CommentResponse.from(comment);
    }

    public void deleteComment(Long id, Long userId) {
        Comment comment = findCommentAndVerifyOwner(id, userId);
        Long freePostId = comment.getFreePostId();
        commentRepository.delete(comment);
        freePostCommentCountService.decrement(freePostId);
    }

    private Comment findCommentAndVerifyOwner(Long id, Long userId) {
        Comment comment = commentRepository.findById(id)
                .orElseThrow(() -> new CommentException(ErrorCode.COMMENT_NOT_FOUND));
        if (!comment.getUserId().equals(userId)) {
            throw new CommentException(ErrorCode.USER_FORBIDDEN);
        }
        return comment;
    }
}
