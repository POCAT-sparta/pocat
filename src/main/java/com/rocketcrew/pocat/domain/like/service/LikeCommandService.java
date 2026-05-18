package com.rocketcrew.pocat.domain.like.service;

import com.rocketcrew.pocat.domain.like.dto.response.LikeResponse;
import com.rocketcrew.pocat.domain.like.entity.Like;
import com.rocketcrew.pocat.domain.like.repository.LikeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
@RequiredArgsConstructor
@Transactional
public class LikeCommandService {

    private final LikeRepository likeRepository;

    public LikeResponse toggleLike(Long userId, Long auctionId) {
        Optional<Like> existing = likeRepository.findByUserIdAndAuctionId(userId, auctionId);
        if (existing.isPresent()) {
            likeRepository.delete(existing.get());
            return LikeResponse.from(existing.get());
        }
        Like like = Like.builder()
                .userId(userId)
                .auctionId(auctionId)
                .build();
        return LikeResponse.from(likeRepository.save(like));
    }
}
