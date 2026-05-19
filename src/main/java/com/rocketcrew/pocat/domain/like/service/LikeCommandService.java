package com.rocketcrew.pocat.domain.like.service;

import com.rocketcrew.pocat.domain.like.dto.response.ToggleLikeResponse;
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

    public ToggleLikeResponse toggleLike(Long userId, Long auctionId) {
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
    }
}
