package com.rocketcrew.pocat.domain.auction.service;

import com.rocketcrew.pocat.domain.auction.entity.Auction;
import com.rocketcrew.pocat.domain.auction.dto.BuyoutCompletion;
import com.rocketcrew.pocat.domain.auction.dto.BuyoutReservation;
import com.rocketcrew.pocat.domain.auction.enums.AuctionStatus;
import com.rocketcrew.pocat.domain.auction.repository.AuctionRepository;
import com.rocketcrew.pocat.domain.bid.entity.AuctionBid;
import com.rocketcrew.pocat.domain.bid.enums.BidStatus;
import com.rocketcrew.pocat.domain.bid.repository.AuctionBidRepository;
import com.rocketcrew.pocat.domain.order.entity.Order;
import com.rocketcrew.pocat.domain.user.entity.User;
import com.rocketcrew.pocat.global.exception.common.ErrorCode;
import com.rocketcrew.pocat.global.exception.domain.AuctionException;
import com.rocketcrew.pocat.global.exception.domain.BidException;
import com.rocketcrew.pocat.global.monitoring.AuctionAnomalyProperties;
import com.rocketcrew.pocat.domain.card.service.CardQueryService;
import com.rocketcrew.pocat.domain.order.dto.response.CardAveragePriceResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.ZoneId;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuctionBuyoutTransactionService {

    private static final ZoneId AUCTION_ZONE = ZoneId.of("Asia/Seoul");

    private final AuctionRepository auctionRepository;
    private final AuctionBidRepository auctionBidRepository;
    private final AuctionAnomalyProperties anomalyProperties;
    private final CardQueryService cardQueryService;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public BuyoutReservation reserveBuyout(Long auctionId, User buyer) {
        // 즉시 구매 대상 경매 엔티티 조회
        Auction auction = findAuction(auctionId);
        // 경매가 즉시 구매 가능한 상태인지 검증
        validateBuyoutAvailable(auction);
        // 구매자가 즉시 구매 가능한 사용자인지 검증
        validateBuyer(buyer, auction);

        Long buyoutPrice = auction.getBuyoutPrice();
        // 경매 상태를 PAYMENT_PENDING으로 바꿔 결제 처리 중 다른 입찰/즉시 구매 차단
        auction.markPaymentPending();

        // 주문 생성에 필요한 경매 정보를 트랜잭션 밖으로 들고 나가기 위한 스냅샷 생성
        return new BuyoutReservation(
                auction.getId(),
                auction.getCardId(),
                auction.getSellerId(),
                buyoutPrice
        );
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean restoreAuctionAfterPaymentFailure(Long auctionId) {
        Auction auction = findAuction(auctionId);
        if (auction.getStatus() == AuctionStatus.PAYMENT_PENDING) {
            auction.restoreActiveFromPaymentPending();
            return true;
        }
        return false;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public BuyoutCompletion completeBuyout(
            BuyoutReservation reservation,
            Long buyerId,
            Order order,
            BuyoutCompletedEventPublisher buyoutCompletedEventPublisher
    ) {
        Auction auction = findAuction(reservation.auctionId());
        if (auction.getStatus() != AuctionStatus.PAYMENT_PENDING) {
            throw new AuctionException(ErrorCode.AUCTION_INVALID_STATUS_TRANSITION);
        }
        // 즉시 구매자의 WON 입찰 생성. 복구 스케줄러의 재시도 상황에는 기존 WON 입찰 재사용
        AuctionBid buyoutBid = findOrCreateWonBuyoutBid(auction, buyerId, reservation.buyoutPrice());
        Long previousHighestBidderId = auction.getHighestBidderId();
        // 즉시구매 입찰을 제외한 기존 입찰들을 LOST 처리
        markExistingBidsLost(auction, buyoutBid.getId());
        // 경매 최고가와 최고 입찰자를 즉시 구매가와 구매자로 갱신
        auction.updateHighestBid(reservation.buyoutPrice(), buyerId);
        // 경매 상태를 PAYMENT_PENDING -> ENDED로 변경합니다.
        auction.endAfterPaymentPending();
        logBuyoutAnomalyIfNeeded(auction, buyerId);
        // 즉시 구매 완료 아웃박스 저장 및 이벤트 발행
        buyoutCompletedEventPublisher.publish(
                auction,
                order,
                buyerId,
                previousHighestBidderId
        );

        return new BuyoutCompletion(auction, buyoutBid);
    }

    private void logBuyoutAnomalyIfNeeded(Auction auction, Long buyerId) {
        Long buyoutPrice = auction.getBuyoutPrice();
        if (buyoutPrice == null) {
            return;
        }
        try {
            CardAveragePriceResponse avg = cardQueryService.getAveragePrice(auction.getCardId());
            Long marketPrice = (avg != null && avg.averagePrice() != null && avg.transactionCount() > 0)
                    ? avg.averagePrice()
                    : auction.getStartingPrice();
            if (marketPrice == null || marketPrice <= 0) {
                return;
            }
            double ratio = (double) buyoutPrice / marketPrice;
            if (ratio > anomalyProperties.getAuctionAnomalyThreshold()) {
                log.warn("[AUCTION_ANOMALY] type=BUYOUT auctionId={} cardId={} sellerId={} buyerId={} finalPrice={} marketPrice={} ratio={}",
                        auction.getId(), auction.getCardId(), auction.getSellerId(), buyerId,
                        buyoutPrice, marketPrice, String.format("%.2f", ratio));
            }
        } catch (Exception e) {
            log.warn("[AUCTION_ANOMALY] 모니터링 로그 실패 — auctionId={} cardId={}", auction.getId(), auction.getCardId(), e);
        }
    }

    private AuctionBid findOrCreateWonBuyoutBid(Auction auction, Long buyerId, Long buyoutPrice) {
        // 이미 생성된 즉시구매 WON 입찰이 있는지 확인
        return auctionBidRepository.findFirstByAuctionIdAndUserIdAndStatusOrderByBidPriceDescCreatedAtDesc(
                        auction.getId(),
                        buyerId,
                        BidStatus.WON
                )
                // 즉시 구매 WON 입찰을 새로 저장
                .orElseGet(() -> auctionBidRepository.save(AuctionBid.builder()
                        .auctionId(auction.getId())
                        .userId(buyerId)
                        .bidPrice(buyoutPrice)
                        .status(BidStatus.WON)
                        .build()));
    }

    private Auction findAuction(Long auctionId) {
        return auctionRepository.findById(auctionId)
                .orElseThrow(() -> new AuctionException(ErrorCode.AUCTION_NOT_FOUND));
    }

    private void validateBuyoutAvailable(Auction auction) {
        // 경매 상태가 ACTIVE인지 검증
        if (auction.getStatus() != AuctionStatus.ACTIVE) {
            throw new AuctionException(ErrorCode.AUCTION_NOT_ACTIVE);
        }
        // 현재 시간이 경매 시작/종료 시간 사이인지 검증
        LocalDateTime now = LocalDateTime.now(AUCTION_ZONE);
        if (auction.getStartedAt() == null
                || auction.getEndedAt() == null
                || now.isBefore(auction.getStartedAt())
                || !now.isBefore(auction.getEndedAt())) {
            throw new AuctionException(ErrorCode.AUCTION_NOT_ACTIVE);
        }
        // 즉시 구매가가 존재하는지 검증
        if (auction.getBuyoutPrice() == null) {
            throw new AuctionException(ErrorCode.AUCTION_PRICE_INVALID);
        }
    }

    private void validateBuyer(User buyer, Auction auction) {
        // 입찰 차단 유저가 아닌지 검증
        if (buyer.isBidBlocked()) {
            throw new BidException(ErrorCode.BID_BLOCKED_USER);
        }
        // 판매자 본인이 아닌지 검증
        if (auction.getSellerId().equals(buyer.getId())) {
            throw new BidException(ErrorCode.BID_SELLER_FORBIDDEN);
        }
        // 빌링키가 등록되어 있는지 검증
        if (!StringUtils.hasText(buyer.getBillingKey())) {
            throw new BidException(ErrorCode.BID_BILLING_KEY_REQUIRED);
        }
    }

    private void markExistingBidsLost(Auction auction, Long buyoutBidId) {
        for (AuctionBid bid : auctionBidRepository.findAllByAuctionId(auction.getId())) {
            if (buyoutBidId.equals(bid.getId())) {
                continue;
            }
            if (bid.getStatus() == BidStatus.LEADING) {
                bid.markOutbid();
                bid.markLost();
            } else if (bid.getStatus() == BidStatus.OUTBID) {
                bid.markLost();
            }
        }
    }

    @FunctionalInterface
    public interface BuyoutCompletedEventPublisher {
        void publish(Auction auction, Order order, Long buyerId, Long previousHighestBidderId);
    }
}
