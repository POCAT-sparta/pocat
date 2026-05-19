package com.rocketcrew.pocat.domain.comment.service;

import com.rocketcrew.pocat.domain.comment.dto.response.CommentTreeResponse;
import com.rocketcrew.pocat.domain.comment.entity.Comment;
import com.rocketcrew.pocat.domain.comment.repository.CommentRepository;
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
public class CommentQueryService {

    private final CommentRepository commentRepository;

    public Page<CommentTreeResponse> getCommentsByPost(Long postId, Pageable pageable) {
        Page<Comment> rootComments = commentRepository.findByPostIdAndParentIdIsNull(postId, pageable);

        List<Long> rootIds = rootComments.getContent().stream()
                .map(Comment::getId)
                .toList();

        Map<Long, List<CommentTreeResponse>> childMap = commentRepository.findByParentIdIn(rootIds)
                .stream()
                .collect(Collectors.groupingBy(
                        Comment::getParentId,
                        Collectors.mapping(CommentTreeResponse::from, Collectors.toList())
                ));

        return rootComments.map(root ->
                CommentTreeResponse.from(root, childMap.getOrDefault(root.getId(), List.of()))
        );
    }
}
