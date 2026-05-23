package com.rocketcrew.pocat.domain.community.freepost.service;

import com.rocketcrew.pocat.domain.community.freepost.dto.response.FreePostResponse;
import com.rocketcrew.pocat.domain.community.freepost.entity.FreePost;
import com.rocketcrew.pocat.domain.community.freepost.repository.FreePostRepository;
import com.rocketcrew.pocat.domain.user.entity.User;
import com.rocketcrew.pocat.domain.user.repository.UserRepository;
import com.rocketcrew.pocat.global.cache.CacheNames;
import com.rocketcrew.pocat.global.exception.common.ErrorCode;
import com.rocketcrew.pocat.global.exception.domain.FreePostException;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class FreePostDetailCacheService {

    private final FreePostRepository freePostRepository;
    private final UserRepository userRepository;
    private final CacheManager cacheManager;

    @Cacheable(value = CacheNames.POST_FREE_DETAIL, key = "#postId", sync = true)
    public FreePostResponse loadPostDetail(Long postId) {
        FreePost freePost = freePostRepository.findById(postId)
                .orElseThrow(() -> new FreePostException(ErrorCode.FREE_POST_NOT_FOUND));
        String nickname = userRepository.findById(freePost.getUserId())
                .map(User::getNickname)
                .orElse("");
        return FreePostResponse.of(freePost, nickname, freePost.getCommentCount());
    }

    @CacheEvict(value = CacheNames.POST_FREE_DETAIL, key = "#postId")
    public void evict(Long postId) {
    }

    public void evictAfterCommit(Long postId) {
        TransactionSynchronizationManager.registerSynchronization(
            new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    Cache cache = cacheManager.getCache(CacheNames.POST_FREE_DETAIL);
                    if (cache != null) {
                        cache.evict(postId);
                    }
                }
            }
        );
    }
}
