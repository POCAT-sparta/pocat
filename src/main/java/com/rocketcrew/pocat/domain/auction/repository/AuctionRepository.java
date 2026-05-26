package com.rocketcrew.pocat.domain.auction.repository;

import com.rocketcrew.pocat.domain.auction.entity.Auction;
import com.rocketcrew.pocat.domain.auction.enums.AuctionStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;

public interface AuctionRepository extends JpaRepository<Auction, Long>, AuctionRepositoryCustom {

    List<Auction> findAllByStatus(AuctionStatus status);

    // ACTIVE 경매 중 현재 실제 노출/입찰 가능 시간에 포함되는 경매만 조회한다.
    List<Auction> findAllByStatusAndStartedAtLessThanEqualAndEndedAtGreaterThan(
            AuctionStatus status,
            LocalDateTime startedAt,
            LocalDateTime endedAt
    );

    // status가 ACTIVE이고 endedAt이 전달된 시각 이하인 경매를 종료 예정 시각 오름차순으로 모두 조회한다.
    // 백업 스케줄러가 Redis 만료 이벤트로 처리되지 않은 경매를 보정 종료할 때 사용한다.
    List<Auction> findAllByStatusAndEndedAtLessThanEqualOrderByEndedAtAsc(
            AuctionStatus status,
            LocalDateTime endedAt
    );
}
