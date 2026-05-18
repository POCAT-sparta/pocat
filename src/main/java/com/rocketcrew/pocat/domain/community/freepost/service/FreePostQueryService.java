package com.rocketcrew.pocat.domain.community.freepost.service;

import com.rocketcrew.pocat.domain.community.freepost.dto.response.FreePostResponse;
import com.rocketcrew.pocat.domain.community.freepost.entity.FreePost;
import com.rocketcrew.pocat.domain.community.freepost.repository.FreePostRepository;
import com.rocketcrew.pocat.global.exception.common.ErrorCode;
import com.rocketcrew.pocat.global.exception.domain.FreePostException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class FreePostQueryService {

    private final FreePostRepository freePostRepository;

    public Page<FreePostResponse> getPosts(Pageable pageable) {
        return freePostRepository.findAll(pageable)
                .map(FreePostResponse::from);
    }

    public FreePostResponse getPost(Long postId) {
        FreePost freePost = freePostRepository.findById(postId)
                .orElseThrow(() -> new FreePostException(ErrorCode.FREE_POST_NOT_FOUND));
        return FreePostResponse.from(freePost);
    }
}
