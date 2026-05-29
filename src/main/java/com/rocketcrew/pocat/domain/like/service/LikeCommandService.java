package com.rocketcrew.pocat.domain.like.service;

import com.rocketcrew.pocat.domain.like.dto.response.ToggleLikeResponse;
import com.rocketcrew.pocat.domain.like.entity.Like;
import com.rocketcrew.pocat.domain.like.repository.LikeRepository;
import com.rocketcrew.pocat.global.exception.common.ErrorCode;
import com.rocketcrew.pocat.global.exception.common.ServiceException;
import com.rocketcrew.pocat.global.exception.domain.LikeException;
import com.rocketcrew.pocat.global.ratelimit.RateLimitProperties;
import com.rocketcrew.pocat.global.ratelimit.RedisRateLimiter;
import lombok.RequiredArgsConstructor;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.Optional;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
@Transactional
public class LikeCommandService {

    private static final String LIKE_LOCK_KEY_PREFIX = "like:lock:";
    private static final long LIKE_LOCK_WAIT_SECONDS = 0L;

    private final LikeRepository likeRepository;
    private final RedissonClient redissonClient;
    private final RedisRateLimiter redisRateLimiter;
    private final RateLimitProperties rateLimitProperties;

    public ToggleLikeResponse toggleLike(Long userId, Long auctionId) {
        if (!redisRateLimiter.isAllowed("rate:user:like:" + userId,
                rateLimitProperties.getLikeLimit(),
                rateLimitProperties.getLikeWindowSeconds())) {
            throw new ServiceException(ErrorCode.RATE_LIMIT_EXCEEDED);
        }
        RLock lock = redissonClient.getLock(LIKE_LOCK_KEY_PREFIX + userId + ":" + auctionId);
        if (!acquireLock(lock)) {
            throw new LikeException(ErrorCode.LIKE_LOCK_FAILED);
        }
        releaseLockAfterTransaction(lock);

        try {
            Optional<Like> existing = likeRepository.findByUserIdAndAuctionId(userId, auctionId);
            if (existing.isPresent()) {
                likeRepository.delete(existing.get());
                return new ToggleLikeResponse(auctionId, false);  // isLiked = false (취소됨)
            }
            Like like = Like.builder()
                    .userId(userId)
                    .auctionId(auctionId)
                    .build();
            likeRepository.save(like);
            return new ToggleLikeResponse(auctionId, true);  // isLiked = true (추가됨)
        } catch (DataIntegrityViolationException e) {
            if (e.getCause() instanceof org.hibernate.exception.ConstraintViolationException) {
                throw new LikeException(ErrorCode.LIKE_DUPLICATE);
            }
            throw e;
        }
    }

    private boolean acquireLock(RLock lock) {
        try {
            return lock.tryLock(LIKE_LOCK_WAIT_SECONDS, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new LikeException(ErrorCode.LIKE_LOCK_FAILED);
        }
    }

    private void releaseLockAfterTransaction(RLock lock) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCompletion(int status) {
                    if (lock.isHeldByCurrentThread()) {
                        lock.unlock();
                    }
                }
            });
        } else {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }
}
