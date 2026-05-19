package com.rocketcrew.pocat.domain.community.freepost.service;

import com.rocketcrew.pocat.domain.community.freepost.dto.request.CreateFreePostRequest;
import com.rocketcrew.pocat.domain.community.freepost.dto.request.UpdateFreePostRequest;
import com.rocketcrew.pocat.domain.community.freepost.dto.response.FreePostResponse;
import com.rocketcrew.pocat.domain.community.freepost.entity.FreePost;
import com.rocketcrew.pocat.domain.community.freepost.repository.FreePostRepository;
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
        return FreePostResponse.from(freePostRepository.save(freePost));
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
        return FreePostResponse.from(freePost);
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
        FreePost freePost = freePostRepository.findById(postId)
                .orElseThrow(() -> new FreePostException(ErrorCode.FREE_POST_NOT_FOUND));
        freePost.incrementViewCount();
    }
}
