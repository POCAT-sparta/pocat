package com.rocketcrew.pocat.domain.community.tradepost.service;

import com.rocketcrew.pocat.domain.community.tradepost.dto.response.TradePostResponse;
import com.rocketcrew.pocat.domain.community.tradepost.entity.TradePost;
import com.rocketcrew.pocat.domain.community.tradepost.repository.TradePostRepository;
import com.rocketcrew.pocat.domain.user.service.UserQueryService;
import com.rocketcrew.pocat.global.cache.CacheNames;
import com.rocketcrew.pocat.global.exception.common.ErrorCode;
import com.rocketcrew.pocat.global.exception.domain.TradePostException;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TradePostDetailCacheService {

    private final TradePostRepository tradePostRepository;
    private final UserQueryService userQueryService;

    @Cacheable(value = CacheNames.POST_TRADE_DETAIL, key = "#id", sync = true)
    public TradePostResponse loadPostDetail(Long id) {
        TradePost tradePost = tradePostRepository.findById(id)
                .orElseThrow(() -> new TradePostException(ErrorCode.TRADE_POST_NOT_FOUND));
        String nickname = userQueryService.getUserById(tradePost.getUserId()).nickname();
        return TradePostResponse.from(tradePost, nickname);
    }

    @CacheEvict(value = CacheNames.POST_TRADE_DETAIL, key = "#id")
    public void evict(Long id) {
    }
}
