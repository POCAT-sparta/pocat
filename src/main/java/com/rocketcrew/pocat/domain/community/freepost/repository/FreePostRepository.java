package com.rocketcrew.pocat.domain.community.freepost.repository;

import com.rocketcrew.pocat.domain.community.freepost.entity.FreePost;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FreePostRepository extends JpaRepository<FreePost, Long> {

    Page<FreePost> findByUserId(Long userId, Pageable pageable);

    Page<FreePost> findByTitleContaining(String keyword, Pageable pageable);
}
