package com.rocketcrew.pocat.domain.auction.service;

import com.rocketcrew.pocat.domain.auction.entity.Auction;
import com.rocketcrew.pocat.domain.auction.enums.AuctionStatus;
import com.rocketcrew.pocat.domain.auction.event.AuctionEndedEvent;
import com.rocketcrew.pocat.domain.auction.repository.AuctionRepository;
import com.rocketcrew.pocat.domain.bid.entity.AuctionBid;
import com.rocketcrew.pocat.domain.bid.enums.BidStatus;
import com.rocketcrew.pocat.domain.bid.repository.AuctionBidRepository;
import com.rocketcrew.pocat.global.metrics.AuctionMetrics;
import com.rocketcrew.pocat.domain.card.service.CardQueryService;
import com.rocketcrew.pocat.domain.order.dto.response.CardAveragePriceResponse;
import com.rocketcrew.pocat.global.monitoring.AuctionAnomalyProperties;
import com.rocketcrew.pocat.global.outbox.service.OutboxEventWriter;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import org.slf4j.LoggerFactory;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class AuctionLifecycleServiceTest {

    @InjectMocks
    AuctionLifecycleService service;

    @Mock
    AuctionRepository auctionRepository;

    @Mock
    AuctionBidRepository auctionBidRepository;

    @Mock
    RedissonClient redissonClient;

    @Mock
    RLock rLock;

    @Mock
    ApplicationEventPublisher eventPublisher;

    @Mock
    AuctionEsIndexService auctionEsIndexService;

    @Mock
    OutboxEventWriter outboxEventWriter;

    @Mock
    AuctionMetrics metrics;

    @Mock
    AuctionAnomalyProperties anomalyProperties;

    @Mock
    CardQueryService cardQueryService;

    @BeforeEach
    void setUp() throws InterruptedException {
        given(redissonClient.getLock(anyString())).willReturn(rLock);
        given(rLock.tryLock(anyLong(), any(TimeUnit.class))).willReturn(true);
        lenient().when(anomalyProperties.getAuctionAnomalyThreshold()).thenReturn(3.0);
        lenient().when(cardQueryService.getAveragePrice(anyLong()))
                .thenReturn(new CardAveragePriceResponse(1L, 1000L, 5L,
                        LocalDateTime.now().minusDays(90), LocalDateTime.now()));
        TransactionSynchronizationManager.initSynchronization();
    }

    @AfterEach
    void tearDown() {
        TransactionSynchronizationManager.clearSynchronization();
    }

    @Test
    @DisplayName("경매 종료 시 LEADING은 WON, OUTBID만 LOST로 전환한다")
    void closeExpiredAuction_marksOnlyOutbidBidsLost() {
        // given
        Auction auction = buildExpiredActiveAuction(1L, 10L);
        AuctionBid winningBid = buildBid(1L, 10L, BidStatus.LEADING);
        AuctionBid outbidBid = buildBid(2L, 20L, BidStatus.OUTBID);
        AuctionBid cancelledBid = buildBid(3L, 30L, BidStatus.CANCELLED);
        AuctionBid alreadyLostBid = buildBid(4L, 40L, BidStatus.LOST);

        given(auctionRepository.findById(1L)).willReturn(Optional.of(auction));
        given(auctionBidRepository.findAllByAuctionId(1L))
                .willReturn(List.of(winningBid, outbidBid, cancelledBid, alreadyLostBid));

        // when
        boolean result = service.closeExpiredAuction(1L);

        // then
        assertThat(result).isTrue();
        assertThat(auction.getStatus()).isEqualTo(AuctionStatus.ENDED);
        assertThat(winningBid.getStatus()).isEqualTo(BidStatus.WON);
        assertThat(outbidBid.getStatus()).isEqualTo(BidStatus.LOST);
        assertThat(cancelledBid.getStatus()).isEqualTo(BidStatus.CANCELLED);
        assertThat(alreadyLostBid.getStatus()).isEqualTo(BidStatus.LOST);
        verify(eventPublisher).publishEvent(ArgumentMatchers.<Object>argThat(event -> {
            if (!(event instanceof AuctionEndedEvent endedEvent)) {
                return false;
            }
            return endedEvent.getAuctionId().equals(1L)
                    && endedEvent.getCardId().equals(1L)
                    && endedEvent.getWinnerId().equals(10L)
                    && endedEvent.getLoserIds().equals(List.of(20L));
        }));
    }

    @Test
    @DisplayName("경매 종료 시 낙찰자가 없으면 NO_BIDDER로 전환하고 낙찰자 없는 종료 이벤트를 발행한다")
    void closeExpiredAuction_marksNoBidderWhenHighestBidderIsNull() {
        // given
        Auction auction = buildExpiredActiveAuctionWithoutBidder(1L);

        given(auctionRepository.findById(1L)).willReturn(Optional.of(auction));
        given(auctionBidRepository.findAllByAuctionId(1L)).willReturn(List.of());

        // when
        boolean result = service.closeExpiredAuction(1L);

        // then
        assertThat(result).isTrue();
        assertThat(auction.getStatus()).isEqualTo(AuctionStatus.NO_BIDDER);
        verify(eventPublisher).publishEvent(ArgumentMatchers.<Object>argThat(event -> {
            if (!(event instanceof AuctionEndedEvent endedEvent)) {
                return false;
            }
            return endedEvent.getAuctionId().equals(1L)
                    && endedEvent.getCardId().equals(1L)
                    && endedEvent.getWinnerId() == null
                    && endedEvent.getSellerId().equals(100L)
                    && endedEvent.getLoserIds().isEmpty()
                    && endedEvent.getFinalPrice() == null;
        }));
    }

    private Auction buildExpiredActiveAuction(Long id, Long highestBidderId) {
        Auction auction = Auction.builder()
                .cardId(1L)
                .sellerId(100L)
                .highestBidderId(highestBidderId)
                .title("테스트 경매")
                .description("설명")
                .startingPrice(1000L)
                .highestPrice(5000L)
                .status(AuctionStatus.ACTIVE)
                .startedAt(LocalDateTime.now().minusDays(3))
                .endedAt(LocalDateTime.now().minusSeconds(1))
                .build();
        ReflectionTestUtils.setField(auction, "id", id);
        return auction;
    }

    private Auction buildExpiredActiveAuctionWithoutBidder(Long id) {
        Auction auction = Auction.builder()
                .cardId(1L)
                .sellerId(100L)
                .title("테스트 경매")
                .description("설명")
                .startingPrice(1000L)
                .status(AuctionStatus.ACTIVE)
                .startedAt(LocalDateTime.now().minusDays(3))
                .endedAt(LocalDateTime.now().minusSeconds(1))
                .build();
        ReflectionTestUtils.setField(auction, "id", id);
        return auction;
    }

    private AuctionBid buildBid(Long id, Long userId, BidStatus status) {
        AuctionBid bid = AuctionBid.builder()
                .auctionId(1L)
                .userId(userId)
                .bidPrice(1000L)
                .status(status)
                .build();
        ReflectionTestUtils.setField(bid, "id", id);
        return bid;
    }

    @Test
    @DisplayName("낙찰가가 시작가의 임계값 초과 시 AUCTION_ANOMALY 로그를 출력한다")
    void closeExpiredAuction_logsAnomalyWhenFinalPriceExceedsThreshold() {
        // given
        Auction auction = buildExpiredActiveAuction(1L, 10L); // startingPrice=1000, highestPrice=5000 (5배)
        AuctionBid winningBid = buildBid(1L, 10L, BidStatus.LEADING);

        given(auctionRepository.findById(1L)).willReturn(Optional.of(auction));
        given(auctionBidRepository.findAllByAuctionId(1L)).willReturn(List.of(winningBid));

        ListAppender<ILoggingEvent> listAppender = new ListAppender<>();
        Logger logger = (Logger) LoggerFactory.getLogger(AuctionLifecycleService.class);
        listAppender.start();
        logger.addAppender(listAppender);

        try {
            // when
            service.closeExpiredAuction(1L);

            // then
            assertThat(listAppender.list)
                    .anyMatch(event -> {
                        String msg = event.getFormattedMessage();
                        return msg.contains("[AUCTION_ANOMALY]")
                                && msg.contains("type=EXPIRED_WIN")
                                && msg.contains("winnerId=10")
                                && msg.contains("finalPrice=5000")
                                && msg.contains("marketPrice=1000")
                                && msg.contains("ratio=5.00");
                    });
        } finally {
            logger.detachAppender(listAppender);
        }
    }

    @Test
    @DisplayName("낙찰가가 시작가의 임계값 이하이면 AUCTION_ANOMALY 로그를 출력하지 않는다")
    void closeExpiredAuction_doesNotLogAnomalyWhenFinalPriceBelowThreshold() {
        // given — highestPrice=1500, startingPrice=1000, ratio=1.5 (임계값 3.0 미만)
        Auction auction = Auction.builder()
                .cardId(1L)
                .sellerId(100L)
                .highestBidderId(10L)
                .title("테스트 경매")
                .description("설명")
                .startingPrice(1000L)
                .highestPrice(1500L)
                .status(AuctionStatus.ACTIVE)
                .startedAt(LocalDateTime.now().minusDays(3))
                .endedAt(LocalDateTime.now().minusSeconds(1))
                .build();
        ReflectionTestUtils.setField(auction, "id", 1L);
        AuctionBid winningBid = buildBid(1L, 10L, BidStatus.LEADING);

        given(auctionRepository.findById(1L)).willReturn(Optional.of(auction));
        given(auctionBidRepository.findAllByAuctionId(1L)).willReturn(List.of(winningBid));

        ListAppender<ILoggingEvent> listAppender = new ListAppender<>();
        Logger logger = (Logger) LoggerFactory.getLogger(AuctionLifecycleService.class);
        listAppender.start();
        logger.addAppender(listAppender);

        try {
            // when
            service.closeExpiredAuction(1L);

            // then
            assertThat(listAppender.list)
                    .noneMatch(event -> event.getFormattedMessage().contains("[AUCTION_ANOMALY]"));
        } finally {
            logger.detachAppender(listAppender);
        }
    }

    @Test
    @DisplayName("낙찰자 없는 경매 종료 시 AUCTION_ANOMALY 로그를 출력하지 않는다")
    void closeExpiredAuction_doesNotLogAnomalyForNoBidder() {
        // given
        Auction auction = buildExpiredActiveAuctionWithoutBidder(1L);

        given(auctionRepository.findById(1L)).willReturn(Optional.of(auction));
        given(auctionBidRepository.findAllByAuctionId(1L)).willReturn(List.of());

        ListAppender<ILoggingEvent> listAppender = new ListAppender<>();
        Logger logger = (Logger) LoggerFactory.getLogger(AuctionLifecycleService.class);
        listAppender.start();
        logger.addAppender(listAppender);

        try {
            // when
            service.closeExpiredAuction(1L);

            // then
            assertThat(listAppender.list)
                    .noneMatch(event -> event.getFormattedMessage().contains("[AUCTION_ANOMALY]"));
        } finally {
            logger.detachAppender(listAppender);
        }
    }
}
