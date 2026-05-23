package com.rocketcrew.pocat.domain.comment.service;

import com.rocketcrew.pocat.domain.comment.dto.response.CommentTreeResponse;
import com.rocketcrew.pocat.domain.comment.entity.Comment;
import com.rocketcrew.pocat.domain.comment.repository.CommentRepository;
import com.rocketcrew.pocat.domain.user.entity.User;
import com.rocketcrew.pocat.domain.user.repository.UserRepository;
import com.rocketcrew.pocat.global.cache.CacheNames;
import com.rocketcrew.pocat.global.cache.CachedPage;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CommentQueryService {

    private final CommentRepository commentRepository;
    private final UserRepository userRepository;

    @Cacheable(
            value = CacheNames.POST_COMMENTS,
            key = "#freePostId + ':page:' + #pageable.pageNumber + ':size:' + #pageable.pageSize",
            sync = true
    )
    public CachedPage<CommentTreeResponse> getCommentsByPost(Long freePostId, Pageable pageable) {
        Page<Comment> rootComments = commentRepository.findByFreePostIdAndParentIdIsNull(freePostId, pageable);

        List<Long> rootIds = rootComments.getContent().stream()
                .map(Comment::getId)
                .toList();

        if (rootIds.isEmpty()) {
            Set<Long> rootUserIds = new HashSet<>();
            rootComments.getContent().forEach(r -> rootUserIds.add(r.getUserId()));
            Map<Long, String> nicknameMap = userRepository.findAllById(rootUserIds)
                    .stream().collect(Collectors.toMap(User::getId, User::getNickname));
            Page<CommentTreeResponse> result = rootComments.map(root ->
                    CommentTreeResponse.of(root, nicknameMap.getOrDefault(root.getUserId(), ""))
            );
            return new CachedPage<>(result);
        }

        List<Comment> children = commentRepository.findByParentIdIn(rootIds);

        Set<Long> allUserIds = new HashSet<>();
        rootComments.getContent().forEach(r -> allUserIds.add(r.getUserId()));
        children.forEach(c -> allUserIds.add(c.getUserId()));

        Map<Long, String> nicknameMap = userRepository.findAllById(allUserIds)
                .stream().collect(Collectors.toMap(User::getId, User::getNickname));

        Map<Long, List<CommentTreeResponse>> childMap = children.stream()
                .collect(Collectors.groupingBy(
                        Comment::getParentId,
                        Collectors.mapping(
                                c -> CommentTreeResponse.of(c, nicknameMap.getOrDefault(c.getUserId(), "")),
                                Collectors.toList()
                        )
                ));

        Page<CommentTreeResponse> result = rootComments.map(root ->
                CommentTreeResponse.of(root, nicknameMap.getOrDefault(root.getUserId(), ""),
                        childMap.getOrDefault(root.getId(), List.of()))
        );
        return new CachedPage<>(result);
    }
}
