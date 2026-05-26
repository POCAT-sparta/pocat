package com.rocketcrew.pocat.domain.ai.assistant.tools;

import com.rocketcrew.pocat.domain.auction.enums.AuctionStatus;
import com.rocketcrew.pocat.domain.auction.repository.AuctionRepository;
import com.rocketcrew.pocat.domain.card.repository.CardRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 경매 조회 Tool for Spring AI Tool Calling.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AuctionTool {

    private final AuctionRepository auctionRepository;
    private final CardRepository cardRepository;

    /**
     * 특정 카드의 현재 진행 중인 경매 목록 조회.
     *
     * @param cardId 카드 ID
     * @return 활성 경매 목록
     */
    @Tool(description = "특정 카드의 현재 진행 중인 경매 목록을 조회합니다. 가격과 남은 시간 정보를 포함합니다.")
    public List<Map<String, Object>> getActiveAuctions(
            @ToolParam(description = "카드 ID") Long cardId
    ) {
        try {
            log.info("Fetching active auctions for cardId: {}", cardId);

            // 카드 존재 확인
            if (!cardRepository.existsById(cardId)) {
                log.warn("Card not found: {}", cardId);
                return List.of();
            }

            return auctionRepository.findByCardIdAndStatus(cardId, AuctionStatus.ACTIVE).stream()
                    .map(auction -> {
                        Map<String, Object> m = new java.util.HashMap<>();
                        m.put("id", auction.getId());
                        m.put("cardId", cardId);
                        m.put("currentPrice", auction.getHighestPrice() != null ? auction.getHighestPrice() : auction.getStartingPrice());
                        m.put("minBidPrice", auction.getStartingPrice());
                        m.put("status", auction.getStatus().toString());
                        m.put("endsAt", auction.getEndedAt() != null ? auction.getEndedAt().toString() : "정보 없음");
                        return m;
                    })
                    .collect(Collectors.toList());
        } catch (Exception e) {
            log.error("Failed to fetch active auctions for cardId: {}", cardId, e);
            return List.of();
        }
    }

    /**
     * 카드의 최근 N일간 낙찰 가격 이력 조회.
     *
     * @param cardId 카드 ID
     * @param days 조회 기간 (7, 14, 30)
     * @return 낙찰 가격 이력
     */
    @Tool(description = "카드의 최근 N일간 낙찰 가격 이력을 조회합니다. 가격 추이를 파악하는 데 도움이 됩니다.")
    public List<Map<String, Object>> getCardPriceHistory(
            @ToolParam(description = "카드 ID") Long cardId,
            @ToolParam(description = "조회 기간(일): 7, 14, 30") Integer days
    ) {
        try {
            log.info("Fetching price history for cardId: {}, days: {}", cardId, days);

            // 카드 존재 확인
            if (!cardRepository.existsById(cardId)) {
                log.warn("Card not found: {}", cardId);
                return List.of();
            }

            // 검증: days는 7, 14, 30만 허용
            if (days != null && !List.of(7, 14, 30).contains(days)) {
                log.warn("Invalid days parameter: {}", days);
                days = 7; // 기본값으로 7일
            }

            java.time.LocalDateTime cutoffDate = java.time.LocalDateTime.now().minusDays(days != null ? days : 7);
            return auctionRepository.findCompletedByCardIdSince(cardId, AuctionStatus.ENDED, cutoffDate, PageRequest.of(0, 50)).stream()
                    .map(auction -> {
                        Map<String, Object> m = new java.util.HashMap<>();
                        m.put("finalPrice", auction.getHighestPrice());
                        m.put("completedAt", auction.getEndedAt() != null ? auction.getEndedAt().toString() : "");
                        return m;
                    })
                    .collect(Collectors.toList());
        } catch (Exception e) {
            log.error("Failed to fetch price history for cardId: {}", cardId, e);
            return List.of();
        }
    }
}
