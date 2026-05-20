package com.rocketcrew.pocat.domain.community.freepost.service;

import com.rocketcrew.pocat.domain.community.freepost.dto.request.CreateFreePostRequest;
import com.rocketcrew.pocat.domain.community.freepost.dto.request.UpdateFreePostRequest;
import com.rocketcrew.pocat.domain.community.freepost.dto.response.FreePostResponse;
import com.rocketcrew.pocat.domain.community.freepost.entity.FreePost;
import com.rocketcrew.pocat.domain.community.freepost.repository.FreePostRepository;
import com.rocketcrew.pocat.domain.user.entity.User;
import com.rocketcrew.pocat.domain.user.repository.UserRepository;
import com.rocketcrew.pocat.global.exception.common.ErrorCode;
import com.rocketcrew.pocat.global.exception.domain.FreePostException;
import com.rocketcrew.pocat.global.exception.domain.UserException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class FreePostCommandService {

    private final FreePostRepository freePostRepository;
    private final UserRepository userRepository;

    public FreePostResponse createPost(Long userId, CreateFreePostRequest request) {
        User user = findUserOrThrow(userId);
        FreePost freePost = FreePost.builder()
                .userId(userId)
                .title(request.title())
                .content(request.content())
                .viewCount(0)
                .build();
        FreePost saved = freePostRepository.save(freePost);
        return FreePostResponse.of(saved, user.getNickname(), 0);
    }

    public FreePostResponse updatePost(Long postId, Long userId, UpdateFreePostRequest request) {
        FreePost freePost = findFreePostAndVerifyOwner(postId, userId);
        validateIfPresent(request.title());
        if (request.title() != null) freePost.updateTitle(request.title());
        validateIfPresent(request.content());
        if (request.content() != null) freePost.updateContent(request.content());
        User user = findUserOrThrow(freePost.getUserId());
        return FreePostResponse.of(freePost, user.getNickname(), freePost.getCommentCount());
    }

    public void deletePost(Long postId, Long userId) {
        FreePost freePost = findFreePostAndVerifyOwner(postId, userId);
        freePostRepository.delete(freePost);
    }

    private FreePost findFreePostAndVerifyOwner(Long postId, Long userId) {
        FreePost freePost = freePostRepository.findById(postId)
                .orElseThrow(() -> new FreePostException(ErrorCode.FREE_POST_NOT_FOUND));
        if (!freePost.getUserId().equals(userId)) {
            throw new FreePostException(ErrorCode.USER_FORBIDDEN);
        }
        return freePost;
    }

    private User findUserOrThrow(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new UserException(ErrorCode.USER_NOT_FOUND));
    }

    private void validateIfPresent(String value) {
        if (value != null && value.isBlank()) {
            throw new FreePostException(ErrorCode.INVALID_CONTENT);
        }
    }
}
