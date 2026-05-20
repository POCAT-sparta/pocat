package com.rocketcrew.pocat.domain.community.freepost.service;

import com.rocketcrew.pocat.domain.comment.repository.CommentRepository;
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
import java.util.stream.StreamSupport;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class FreePostQueryService {

    private final FreePostRepository freePostRepository;
    private final UserRepository userRepository;
    private final CommentRepository commentRepository;

    public Page<FreePostResponse> getPosts(String keyword, Pageable pageable) {
        Page<FreePost> posts = freePostRepository.searchPosts(keyword, pageable);

        List<Long> userIds = posts.getContent().stream()
                .map(FreePost::getUserId)
                .distinct()
                .toList();

        Map<Long, String> nicknameMap = userRepository.findAllById(userIds)
                .stream()
                .collect(Collectors.toMap(User::getId, User::getNickname));

        List<Long> postIds = posts.getContent().stream().map(FreePost::getId).toList();
        Map<Long, Integer> commentCountMap = buildCommentCountMap(postIds);

        return posts.map(post -> FreePostResponse.of(
                post,
                nicknameMap.getOrDefault(post.getUserId(), ""),
                commentCountMap.getOrDefault(post.getId(), 0)
        ));
    }

    public FreePostResponse getPost(Long postId) {
        FreePost freePost = freePostRepository.findById(postId)
                .orElseThrow(() -> new FreePostException(ErrorCode.FREE_POST_NOT_FOUND));

        String nickname = userRepository.findById(freePost.getUserId())
                .map(User::getNickname)
                .orElse("");

        int commentCount = commentRepository.countByFreePostId(postId);

        return FreePostResponse.of(freePost, nickname, commentCount);
    }

    public Page<FreePostResponse> getMyPosts(Long userId, Pageable pageable) {
        Page<FreePost> posts = freePostRepository.findByUserId(userId, pageable);

        String nickname = userRepository.findById(userId)
                .map(User::getNickname)
                .orElse("");

        List<Long> postIds = posts.getContent().stream().map(FreePost::getId).toList();
        Map<Long, Integer> commentCountMap = buildCommentCountMap(postIds);

        return posts.map(post -> FreePostResponse.of(
                post,
                nickname,
                commentCountMap.getOrDefault(post.getId(), 0)
        ));
    }

    private Map<Long, Integer> buildCommentCountMap(List<Long> postIds) {
        if (postIds.isEmpty()) {
            return Map.of();
        }
        return commentRepository.findCommentCountsByFreePostIds(postIds)
                .stream()
                .collect(Collectors.toMap(
                        row -> (Long) row[0],
                        row -> ((Long) row[1]).intValue()
                ));
    }
}
