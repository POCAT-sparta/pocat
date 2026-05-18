package com.rocketcrew.pocat.domain.community.tradepost.repository;

import com.rocketcrew.pocat.domain.community.tradepost.entity.TradePost;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TradePostRepository extends JpaRepository<TradePost, Long> {

    Page<TradePost> findByUserId(Long userId, Pageable pageable);

    Page<TradePost> findByTitleContaining(String keyword, Pageable pageable);

    Page<TradePost> findByPriceBetween(Long minPrice, Long maxPrice, Pageable pageable);
}
