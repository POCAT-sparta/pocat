package com.rocketcrew.pocat.domain.community.freepost.repository;

import com.rocketcrew.pocat.domain.community.freepost.entity.FreePost;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface FreePostRepository extends JpaRepository<FreePost, Long>, FreePostRepositoryCustom {

    Page<FreePost> findByUserId(Long userId, Pageable pageable);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update FreePost f set f.viewCount = f.viewCount + 1 where f.id = :postId")
    int incrementViewCount(@Param("postId") Long postId);
}
