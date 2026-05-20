package com.rocketcrew.pocat.domain.community.tradepost.service;

import com.rocketcrew.pocat.domain.community.tradepost.dto.response.TradePostListResponse;
import com.rocketcrew.pocat.domain.community.tradepost.dto.response.TradePostResponse;
import com.rocketcrew.pocat.domain.community.tradepost.entity.TradePost;
import com.rocketcrew.pocat.domain.community.tradepost.repository.TradePostRepository;
import com.rocketcrew.pocat.domain.user.service.UserQueryService;
import com.rocketcrew.pocat.global.exception.common.ErrorCode;
import com.rocketcrew.pocat.global.exception.domain.TradePostException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TradePostQueryService {

    private final TradePostRepository tradePostRepository;
    private final UserQueryService userQueryService;
    private final ViewCountService viewCountService;

    public Page<TradePostListResponse> getPostsByUserId(Long userId, Pageable pageable) {
        return tradePostRepository.findByUserId(userId, pageable)
                .map(post -> {
                    String nickname = userQueryService.getUserById(post.getUserId()).nickname();
                    return TradePostListResponse.from(post, nickname);
                });
    }

    public Page<TradePostListResponse> getPosts(String keyword, Long minPrice, Long maxPrice, Pageable pageable) {
        return tradePostRepository.searchPosts(keyword, minPrice, maxPrice, pageable)
                .map(post -> {
                    String nickname = userQueryService.getUserById(post.getUserId()).nickname();
                    return TradePostListResponse.from(post, nickname);
                });
    }

    public TradePostResponse getPost(Long id, String clientIp, Long requesterId) {
        TradePost tradePost = tradePostRepository.findById(id)
                .orElseThrow(() -> new TradePostException(ErrorCode.TRADE_POST_NOT_FOUND));
        String nickname = userQueryService.getUserById(tradePost.getUserId()).nickname();
        if (!tradePost.getUserId().equals(requesterId)) {
            viewCountService.increaseViewCount(id, clientIp);
        }
        return TradePostResponse.from(tradePost, nickname);
    }
}
