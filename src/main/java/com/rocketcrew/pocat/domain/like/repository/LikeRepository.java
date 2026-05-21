package com.rocketcrew.pocat.domain.like.repository;

import com.rocketcrew.pocat.domain.like.entity.Like;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface LikeRepository extends JpaRepository<Like, Long> {

    Optional<Like> findByUserIdAndAuctionId(Long userId, Long auctionId);

    Page<Like> findByUserId(Long userId, Pageable pageable);

    boolean existsByUserIdAndAuctionId(Long userId, Long auctionId);

    long countByAuctionId(Long auctionId);
}
