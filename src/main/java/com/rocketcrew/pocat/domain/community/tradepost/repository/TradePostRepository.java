package com.rocketcrew.pocat.domain.community.tradepost.repository;

import com.rocketcrew.pocat.domain.community.tradepost.entity.TradePost;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;


public interface TradePostRepository extends JpaRepository<TradePost, Long>, TradePostRepositoryCustom {

    Page<TradePost> findByUserId(Long userId, Pageable pageable);

    @Modifying
    @Query("UPDATE TradePost t SET t.viewCount = t.viewCount + :count WHERE t.id = :postId")
    void increaseViewCount(@Param("postId") Long postId, @Param("count") int count);
}
