package com.rocketcrew.pocat.domain.monitoring.service;

import com.rocketcrew.pocat.domain.auction.enums.AuctionStatus;
import com.rocketcrew.pocat.domain.auction.repository.AuctionRepository;
import com.rocketcrew.pocat.domain.monitoring.dto.DailyStatsResponse;
import com.rocketcrew.pocat.domain.order.repository.OrderRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InternalStatsServiceTest {

    @Mock
    private AuctionRepository auctionRepository;

    @Mock
    private OrderRepository orderRepository;

    @InjectMocks
    private InternalStatsService statsService;

    @Test
    void getDailyStats_returnsCorrectStatsForYesterday() {
        when(auctionRepository.countByCreatedAtBetween(any(), any())).thenReturn(10L);
        when(auctionRepository.countByStatus(AuctionStatus.ACTIVE)).thenReturn(50L);
        when(auctionRepository.countByStatus(AuctionStatus.ENDED)).thenReturn(200L);
        when(auctionRepository.countByStatus(AuctionStatus.NO_BIDDER)).thenReturn(30L);
        when(orderRepository.countByCreatedAtBetween(any(), any())).thenReturn(8L);
        when(orderRepository.sumFinalPriceByCreatedAtBetween(any(), any())).thenReturn(1600000L);

        DailyStatsResponse result = statsService.getDailyStats();

        assertThat(result.reportDate()).isEqualTo(LocalDate.now().minusDays(1));
        assertThat(result.newAuctions()).isEqualTo(10L);
        assertThat(result.completedOrders()).isEqualTo(8L);
        assertThat(result.totalTradeVolume()).isEqualTo(1600000L);
        assertThat(result.activeAuctions()).isEqualTo(50L);
        assertThat(result.endedAuctions()).isEqualTo(200L);
        assertThat(result.noBidderAuctions()).isEqualTo(30L);
    }

    @Test
    void getDailyStats_nullSumReturnsZero() {
        when(auctionRepository.countByCreatedAtBetween(any(), any())).thenReturn(0L);
        when(auctionRepository.countByStatus(any())).thenReturn(0L);
        when(orderRepository.countByCreatedAtBetween(any(), any())).thenReturn(0L);
        when(orderRepository.sumFinalPriceByCreatedAtBetween(any(), any())).thenReturn(null);

        DailyStatsResponse result = statsService.getDailyStats();

        assertThat(result.totalTradeVolume()).isEqualTo(0L);
    }
}
