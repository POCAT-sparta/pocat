package com.rocketcrew.pocat.domain.monitoring.service;

import com.rocketcrew.pocat.domain.auction.enums.AuctionStatus;
import com.rocketcrew.pocat.domain.auction.repository.AuctionRepository;
import com.rocketcrew.pocat.domain.monitoring.dto.DailyStatsResponse;
import com.rocketcrew.pocat.domain.order.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Objects;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class InternalStatsService {

    private final AuctionRepository auctionRepository;
    private final OrderRepository orderRepository;

    public DailyStatsResponse getDailyStats() {
        LocalDate yesterday = LocalDate.now().minusDays(1);
        LocalDateTime start = yesterday.atStartOfDay();
        LocalDateTime end = start.plusDays(1);

        return new DailyStatsResponse(
                yesterday,
                auctionRepository.countByCreatedAtBetween(start, end),
                orderRepository.countByCreatedAtBetween(start, end),
                Objects.requireNonNullElse(orderRepository.sumFinalPriceByCreatedAtBetween(start, end), 0L),
                auctionRepository.countByStatus(AuctionStatus.ACTIVE),
                auctionRepository.countByStatus(AuctionStatus.ENDED),
                auctionRepository.countByStatus(AuctionStatus.NO_BIDDER)
        );
    }
}
