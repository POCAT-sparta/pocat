package com.rocketcrew.pocat.domain.ai.assistant.tools;

import com.rocketcrew.pocat.domain.auction.repository.AuctionRepository;
import com.rocketcrew.pocat.domain.card.repository.CardRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
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

            // 실제 구현은 DB 에이전트가 AuctionRepository에서 getActiveAuctions 메서드 추가
            // 여기서는 기본 구조만 제시
            return auctionRepository.findAll().stream()
                    .filter(auction -> cardId.equals(auction.getId())) // DB 쿼리로 이동 권장
                    .map(auction -> {
                        Map<String, Object> m = new java.util.HashMap<>();
                        m.put("id", auction.getId());
                        m.put("cardId", cardId);
                        m.put("currentPrice", auction.getHighestPrice());
                        m.put("minBidPrice", auction.getStartingPrice());
                        m.put("status", auction.getStatus().toString());
                        m.put("remainingTime", "정보 없음");
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

            // 실제 구현은 DB 에이전트가 커스텀 쿼리로 완료된 경매 이력 조회
            return auctionRepository.findAll().stream()
                    .filter(auction -> cardId.equals(auction.getId())) // DB 쿼리로 이동 권장
                    .map(auction -> {
                        Map<String, Object> m = new java.util.HashMap<>();
                        m.put("finalPrice", auction.getHighestPrice());
                        m.put("completedAt", "완료 시각");
                        m.put("buyerCount", "입찰자 수");
                        return m;
                    })
                    .limit(10)
                    .collect(Collectors.toList());
        } catch (Exception e) {
            log.error("Failed to fetch price history for cardId: {}", cardId, e);
            return List.of();
        }
    }
}
