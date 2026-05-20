package com.rocketcrew.pocat.domain.comment.repository;

import com.rocketcrew.pocat.domain.comment.entity.Comment;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface CommentRepository extends JpaRepository<Comment, Long> {

    List<Comment> findByFreePostId(Long freePostId);

    List<Comment> findByFreePostIdAndParentIdIsNull(Long freePostId);

    Page<Comment> findByFreePostIdAndParentIdIsNull(Long freePostId, Pageable pageable);

    List<Comment> findByParentId(Long parentId);

    List<Comment> findByParentIdIn(List<Long> parentIds);

    int countByFreePostId(Long freePostId);

    @Query("SELECT c.freePostId, COUNT(c) FROM Comment c WHERE c.freePostId IN :freePostIds GROUP BY c.freePostId")
    List<Object[]> findCommentCountsByFreePostIds(@Param("freePostIds") Collection<Long> freePostIds);
}
