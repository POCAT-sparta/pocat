package com.rocketcrew.pocat.domain.community.freepost.service;

import com.rocketcrew.pocat.domain.community.freepost.dto.response.FreePostResponse;
import com.rocketcrew.pocat.domain.community.freepost.entity.FreePost;
import com.rocketcrew.pocat.domain.community.freepost.repository.FreePostRepository;
import com.rocketcrew.pocat.domain.user.entity.User;
import com.rocketcrew.pocat.domain.user.repository.UserRepository;
import com.rocketcrew.pocat.global.exception.common.ErrorCode;
import com.rocketcrew.pocat.global.exception.domain.FreePostException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class FreePostQueryService {

    private final FreePostRepository freePostRepository;
    private final UserRepository userRepository;
    private final FreePostViewCountService viewCountService;
    private final FreePostDetailCacheService freePostDetailCacheService;

    public Page<FreePostResponse> getPosts(String keyword, Pageable pageable) {
        Page<FreePost> posts = freePostRepository.searchPosts(keyword, pageable);

        List<Long> userIds = posts.getContent().stream()
                .map(FreePost::getUserId)
                .distinct()
                .toList();

        Map<Long, String> nicknameMap = userRepository.findAllById(userIds)
                .stream()
                .collect(Collectors.toMap(User::getId, User::getNickname));

        return posts.map(post -> FreePostResponse.of(
                post,
                nicknameMap.getOrDefault(post.getUserId(), ""),
                post.getCommentCount()
        ));
    }

    public FreePostResponse getPost(Long postId, String clientIp, Long requesterId) {
        FreePost freePost = freePostRepository.findById(postId)
                .orElseThrow(() -> new FreePostException(ErrorCode.FREE_POST_NOT_FOUND));

        if (!freePost.getUserId().equals(requesterId)) {
            viewCountService.increaseViewCount(postId, clientIp);
        }

        return freePostDetailCacheService.loadPostDetail(postId);
    }

    public Page<FreePostResponse> getMyPosts(Long userId, Pageable pageable) {
        Page<FreePost> posts = freePostRepository.findByUserId(userId, pageable);

        String nickname = userRepository.findById(userId)
                .map(User::getNickname)
                .orElse("");

        return posts.map(post -> FreePostResponse.of(
                post,
                nickname,
                post.getCommentCount()
        ));
    }
}
