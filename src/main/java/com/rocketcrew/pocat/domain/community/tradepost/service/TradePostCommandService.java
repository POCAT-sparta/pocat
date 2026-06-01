package com.rocketcrew.pocat.domain.community.tradepost.service;

import com.rocketcrew.pocat.domain.ai.rag.event.TradePostEmbeddingEvent;
import com.rocketcrew.pocat.domain.community.tradepost.dto.request.CreateTradePostRequest;
import com.rocketcrew.pocat.domain.community.tradepost.dto.request.UpdateTradePostRequest;
import com.rocketcrew.pocat.domain.community.tradepost.dto.response.CreateTradePost;
import com.rocketcrew.pocat.domain.community.tradepost.dto.response.UpdateTradePostResponse;
import com.rocketcrew.pocat.domain.community.tradepost.entity.TradePost;
import com.rocketcrew.pocat.domain.community.tradepost.repository.TradePostRepository;
import com.rocketcrew.pocat.domain.user.enums.UserRole;
import com.rocketcrew.pocat.global.cache.CacheNames;
import com.rocketcrew.pocat.global.exception.common.ErrorCode;
import com.rocketcrew.pocat.global.exception.common.ServiceException;
import com.rocketcrew.pocat.global.exception.domain.TradePostException;
import com.rocketcrew.pocat.global.ratelimit.RateLimitProperties;
import com.rocketcrew.pocat.global.ratelimit.RedisRateLimiter;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class TradePostCommandService {

    private final TradePostRepository tradePostRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final RedisRateLimiter redisRateLimiter;
    private final RateLimitProperties rateLimitProperties;

    public CreateTradePost createPost(Long userId, CreateTradePostRequest request) {
        if (!redisRateLimiter.isAllowed("rate:user:post:" + userId,
                rateLimitProperties.getPostLimit(),
                rateLimitProperties.getPostWindowSeconds())) {
            throw new ServiceException(ErrorCode.RATE_LIMIT_EXCEEDED);
        }
        TradePost tradePost = TradePost.builder()
                .userId(userId)
                .title(request.title())
                .content(request.content())
                .price(request.price())
                .thumbnail(request.thumbnail())
                .viewCount(0)
                .build();
        tradePost = tradePostRepository.save(tradePost);
        
        // Publish embedding event for RAG after trade post creation
        eventPublisher.publishEvent(new TradePostEmbeddingEvent(tradePost.getId(), tradePost.getContent()));
        
        return CreateTradePost.from(tradePost);
    }

    @CacheEvict(value = CacheNames.POST_TRADE_DETAIL, key = "#id")
    public UpdateTradePostResponse updatePost(Long id, Long userId, UpdateTradePostRequest request) {
        TradePost tradePost = tradePostRepository.findById(id)
                .orElseThrow(() -> new TradePostException(ErrorCode.TRADE_POST_NOT_FOUND));
        if (!tradePost.getUserId().equals(userId)) {
            throw new TradePostException(ErrorCode.USER_FORBIDDEN);
        }
        tradePost.update(request.title(), request.content(), request.price(), request.thumbnail());
        eventPublisher.publishEvent(new TradePostEmbeddingEvent(tradePost.getId(), tradePost.getContent()));
        return UpdateTradePostResponse.from(tradePost);
    }

    @CacheEvict(value = CacheNames.POST_TRADE_DETAIL, key = "#id")
    public void deletePost(Long id, Long userId, String role) {
        boolean isAdmin = UserRole.ADMIN.name().equals(role);
        if (!isAdmin && !redisRateLimiter.isAllowed("rate:user:post:" + userId,
                rateLimitProperties.getPostLimit(),
                rateLimitProperties.getPostWindowSeconds())) {
            throw new ServiceException(ErrorCode.RATE_LIMIT_EXCEEDED);
        }
        TradePost tradePost = tradePostRepository.findById(id)
                .orElseThrow(() -> new TradePostException(ErrorCode.TRADE_POST_NOT_FOUND));

        boolean isOwner = tradePost.getUserId().equals(userId);

        if (!isOwner && !isAdmin) {
            throw new TradePostException(ErrorCode.USER_FORBIDDEN);
        }

        tradePostRepository.delete(tradePost);
    }
}
