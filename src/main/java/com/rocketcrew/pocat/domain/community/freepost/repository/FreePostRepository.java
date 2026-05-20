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

    @Modifying
    @Query("UPDATE FreePost f SET f.viewCount = f.viewCount + :count WHERE f.id = :postId")
    void increaseViewCount(@Param("postId") Long postId, @Param("count") int count);
}
