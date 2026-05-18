package com.rocketcrew.pocat.domain.community.tradepost.service;

import com.rocketcrew.pocat.domain.community.tradepost.dto.response.TradePostListResponse;
import com.rocketcrew.pocat.domain.community.tradepost.dto.response.TradePostResponse;
import com.rocketcrew.pocat.domain.community.tradepost.entity.TradePost;
import com.rocketcrew.pocat.domain.community.tradepost.repository.TradePostRepository;
import com.rocketcrew.pocat.domain.user.entity.User;
import com.rocketcrew.pocat.domain.user.repository.UserRepository;
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
    private final UserRepository userRepository;

    public Page<TradePostListResponse> getPosts(Pageable pageable) {
        return tradePostRepository.findAll(pageable)
                .map(post -> {
                    String nickname = userRepository.findById(post.getUserId())
                            .map(User::getNickname)
                            .orElse(null);
                    return TradePostListResponse.from(post, nickname);
                });
    }

    public TradePostResponse getPost(Long id) {
        TradePost tradePost = tradePostRepository.findById(id)
                .orElseThrow(() -> new TradePostException(ErrorCode.TRADE_POST_NOT_FOUND));
        String nickname = userRepository.findById(tradePost.getUserId())
                .map(User::getNickname)
                .orElse(null);
        return TradePostResponse.from(tradePost, nickname);
    }
}
