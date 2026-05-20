package com.rocketcrew.pocat.domain.community.tradepost.repository;

import com.rocketcrew.pocat.domain.community.tradepost.entity.TradePost;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface TradePostRepositoryCustom {

    Page<TradePost> searchPosts(String keyword, Long minPrice, Long maxPrice, Pageable pageable);
}
