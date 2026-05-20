package com.rocketcrew.pocat.domain.community.freepost.repository;

import com.rocketcrew.pocat.domain.community.freepost.entity.FreePost;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface FreePostRepositoryCustom {
    Page<FreePost> searchPosts(String keyword, Pageable pageable);
}
