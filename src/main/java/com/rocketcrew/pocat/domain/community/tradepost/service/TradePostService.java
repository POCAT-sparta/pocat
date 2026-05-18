package com.rocketcrew.pocat.domain.community.tradepost.service;

import com.rocketcrew.pocat.domain.community.tradepost.dto.request.CreateTradePostRequest;
import com.rocketcrew.pocat.domain.community.tradepost.dto.request.UpdateTradePostRequest;
import com.rocketcrew.pocat.domain.community.tradepost.dto.response.TradePostResponse;
import com.rocketcrew.pocat.domain.community.tradepost.entity.TradePost;
import com.rocketcrew.pocat.domain.community.tradepost.repository.TradePostRepository;
import com.rocketcrew.pocat.global.exception.common.ErrorCode;
import com.rocketcrew.pocat.global.exception.domain.TradePostException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class TradePostService {

    private final TradePostRepository tradePostRepository;

    @Transactional(readOnly = true)
    public Page<TradePostResponse> getPosts(Pageable pageable) {
        return tradePostRepository.findAll(pageable)
                .map(TradePostResponse::from);
    }

    @Transactional(readOnly = true)
    public TradePostResponse getPost(Long id) {
        TradePost tradePost = tradePostRepository.findById(id)
                .orElseThrow(() -> new TradePostException(ErrorCode.TRADE_POST_NOT_FOUND));
        return TradePostResponse.from(tradePost);
    }

    public TradePostResponse createPost(Long userId, CreateTradePostRequest request) {
        TradePost tradePost = TradePost.builder()
                .userId(userId)
                .title(request.title())
                .content(request.content())
                .price(request.price())
                .thumbnail(request.thumbnail())
                .viewCount(0)
                .build();
        return TradePostResponse.from(tradePostRepository.save(tradePost));
    }

    public TradePostResponse updatePost(Long id, Long userId, UpdateTradePostRequest request) {
        TradePost tradePost = tradePostRepository.findById(id)
                .orElseThrow(() -> new TradePostException(ErrorCode.TRADE_POST_NOT_FOUND));
        if (!tradePost.getUserId().equals(userId)) {
            throw new TradePostException(ErrorCode.USER_FORBIDDEN);
        }
        tradePost.update(request.title(), request.content(), request.price(), request.thumbnail());
        return TradePostResponse.from(tradePost);
    }

    public void deletePost(Long id, Long userId) {
        TradePost tradePost = tradePostRepository.findById(id)
                .orElseThrow(() -> new TradePostException(ErrorCode.TRADE_POST_NOT_FOUND));
        if (!tradePost.getUserId().equals(userId)) {
            throw new TradePostException(ErrorCode.USER_FORBIDDEN);
        }
        tradePostRepository.delete(tradePost);
    }
}
