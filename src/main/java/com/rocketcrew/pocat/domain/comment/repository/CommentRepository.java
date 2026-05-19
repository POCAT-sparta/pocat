package com.rocketcrew.pocat.domain.comment.repository;

import com.rocketcrew.pocat.domain.comment.entity.Comment;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CommentRepository extends JpaRepository<Comment, Long> {

    List<Comment> findByFreePostId(Long freePostId);

    List<Comment> findByFreePostIdAndParentIdIsNull(Long freePostId);

    Page<Comment> findByFreePostIdAndParentIdIsNull(Long freePostId, Pageable pageable);

    List<Comment> findByParentId(Long parentId);

    List<Comment> findByParentIdIn(List<Long> parentIds);

    int countByFreePostId(Long freePostId);
}
