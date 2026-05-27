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

import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TradePostQueryService {

    private final TradePostRepository tradePostRepository;
    private final UserQueryService userQueryService;
    private final ViewCountService viewCountService;
    private final TradePostDetailCacheService tradePostDetailCacheService;

    public Page<TradePostListResponse> getPostsByUserId(Long userId, Pageable pageable) {
        String nickname = userQueryService.getUserById(userId).nickname();
        return tradePostRepository.findByUserId(userId, pageable)
                .map(post -> TradePostListResponse.from(post, nickname));
    }

    public Page<TradePostListResponse> getPosts(String keyword, Long minPrice, Long maxPrice, Pageable pageable) {
        Page<TradePost> postPage = tradePostRepository.searchPosts(keyword, minPrice, maxPrice, pageable);
        List<Long> userIds = postPage.getContent().stream()
                .map(TradePost::getUserId)
                .distinct()
                .toList();

        Map<Long, String> nicknameByUserId = userQueryService.getNicknamesByUserIds(userIds);

        return postPage.map(post -> TradePostListResponse.from(
                post,
                nicknameByUserId.get(post.getUserId())
        ));
    }

    public TradePostResponse getPost(Long id, String clientIp, Long requesterId) {
        TradePost tradePost = tradePostRepository.findById(id)
                .orElseThrow(() -> new TradePostException(ErrorCode.TRADE_POST_NOT_FOUND));
        if (!tradePost.getUserId().equals(requesterId)) {
            viewCountService.increaseViewCount(id, clientIp);
        }
        return tradePostDetailCacheService.loadPostDetail(id);
    }
}
