package com.rocketcrew.pocat.domain.like.repository;

import com.rocketcrew.pocat.domain.like.entity.Like;
import com.rocketcrew.pocat.domain.auction.ranking.dto.AuctionCountProjection;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface LikeRepository extends JpaRepository<Like, Long> {

    Optional<Like> findByUserIdAndAuctionId(Long userId, Long auctionId);

    Page<Like> findByUserId(Long userId, Pageable pageable);

    boolean existsByUserIdAndAuctionId(Long userId, Long auctionId);

    long countByAuctionId(Long auctionId);

    @Query("SELECT l.auctionId AS auctionId, COUNT(l) AS cnt FROM Like l WHERE l.auctionId IN :ids GROUP BY l.auctionId")
    List<AuctionCountProjection> countByAuctionIdIn(@Param("ids") List<Long> ids);
}
