package com.rocketcrew.pocat.domain.community.freepost.service;

import com.rocketcrew.pocat.domain.comment.repository.CommentRepository;
import com.rocketcrew.pocat.domain.community.freepost.dto.request.CreateFreePostRequest;
import com.rocketcrew.pocat.domain.community.freepost.dto.request.UpdateFreePostRequest;
import com.rocketcrew.pocat.domain.community.freepost.dto.response.FreePostResponse;
import com.rocketcrew.pocat.domain.community.freepost.entity.FreePost;
import com.rocketcrew.pocat.domain.community.freepost.repository.FreePostRepository;
import com.rocketcrew.pocat.domain.user.repository.UserRepository;
import com.rocketcrew.pocat.global.exception.common.ErrorCode;
import com.rocketcrew.pocat.global.exception.domain.FreePostException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class FreePostCommandService {

    private final FreePostRepository freePostRepository;
    private final UserRepository userRepository;
    private final CommentRepository commentRepository;

    public FreePostResponse createPost(Long userId, CreateFreePostRequest request) {
        if (request.title() == null || request.title().isBlank()) {
            throw new FreePostException(ErrorCode.INVALID_CONTENT);
        }
        if (request.content() == null || request.content().isBlank()) {
            throw new FreePostException(ErrorCode.INVALID_CONTENT);
        }
        FreePost freePost = FreePost.builder()
                .userId(userId)
                .title(request.title())
                .content(request.content())
                .viewCount(0)
                .build();
        FreePost saved = freePostRepository.save(freePost);
        String nickname = userRepository.findById(userId).map(u -> u.getNickname()).orElse("");
        return FreePostResponse.of(saved, nickname, 0);
    }

    public FreePostResponse updatePost(Long postId, Long userId, UpdateFreePostRequest request) {
        FreePost freePost = freePostRepository.findById(postId)
                .orElseThrow(() -> new FreePostException(ErrorCode.FREE_POST_NOT_FOUND));
        if (!freePost.getUserId().equals(userId)) {
            throw new FreePostException(ErrorCode.USER_FORBIDDEN);
        }
        if (request.title() != null) {
            if (request.title().isBlank()) {
                throw new FreePostException(ErrorCode.INVALID_CONTENT);
            }
            freePost.updateTitle(request.title());
        }
        if (request.content() != null) {
            if (request.content().isBlank()) {
                throw new FreePostException(ErrorCode.INVALID_CONTENT);
            }
            freePost.updateContent(request.content());
        }
        String nickname = userRepository.findById(freePost.getUserId()).map(u -> u.getNickname()).orElse("");
        int commentCount = commentRepository.countByFreePostId(freePost.getId());
        return FreePostResponse.of(freePost, nickname, commentCount);
    }

    public void deletePost(Long postId, Long userId) {
        FreePost freePost = freePostRepository.findById(postId)
                .orElseThrow(() -> new FreePostException(ErrorCode.FREE_POST_NOT_FOUND));
        if (!freePost.getUserId().equals(userId)) {
            throw new FreePostException(ErrorCode.USER_FORBIDDEN);
        }
        freePostRepository.delete(freePost);
    }

    public void incrementViewCount(Long postId) {
        int updated = freePostRepository.incrementViewCount(postId);
        if (updated == 0) {
            throw new FreePostException(ErrorCode.FREE_POST_NOT_FOUND);
        }
    }
}
