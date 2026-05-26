package com.rocketcrew.pocat.domain.ai.assistant.tools;

import com.rocketcrew.pocat.domain.bid.repository.AuctionBidRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 입찰 조회 Tool for Spring AI Tool Calling.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BidTool {

    private final AuctionBidRepository bidRepository;

    /**
     * 사용자의 입찰 이력 조회.
     *
     * @param userId 사용자 ID
     * @return 입찰 이력 목록
     */
    @Tool(description = "사용자의 최근 입찰 이력을 조회합니다. 입찰가, 카드 정보, 상태를 포함합니다.")
    public List<Map<String, Object>> getUserBidHistory(
            @ToolParam(description = "사용자 ID") Long userId
    ) {
        try {
            log.info("Fetching bid history for userId: {}", userId);

            // 실제 구현은 DB 에이전트가 BidRepository에서 findByBidderId 등 메서드 추가
            // 여기서는 기본 구조만 제시
            return bidRepository.findAll().stream()
                    .filter(bid -> userId.equals(bid.getId())) // DB 쿼리로 이동 권장
                    .map(bid -> {
                        Map<String, Object> m = new java.util.HashMap<>();
                        m.put("id", bid.getId());
                        m.put("auctionId", bid.getAuctionId());
                        m.put("bidPrice", bid.getBidPrice());
                        m.put("status", bid.getStatus().toString());
                        m.put("createdAt", bid.getCreatedAt() != null ? bid.getCreatedAt().toString() : "");
                        return m;
                    })
                    .limit(10)
                    .collect(Collectors.toList());
        } catch (Exception e) {
            log.error("Failed to fetch bid history for userId: {}", userId, e);
            return List.of();
        }
    }
}
