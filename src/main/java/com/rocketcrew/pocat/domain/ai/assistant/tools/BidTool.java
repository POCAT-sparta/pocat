package com.rocketcrew.pocat.domain.ai.assistant.tools;

import com.rocketcrew.pocat.domain.bid.repository.AuctionBidRepository;
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

            return bidRepository.findByUserIdOrderByCreatedAtDesc(userId, PageRequest.of(0, 10)).stream()
                    .map(bid -> {
                        Map<String, Object> m = new java.util.HashMap<>();
                        m.put("id", bid.getId());
                        m.put("auctionId", bid.getAuctionId());
                        m.put("bidPrice", bid.getBidPrice());
                        m.put("status", bid.getStatus().toString());
                        m.put("createdAt", bid.getCreatedAt() != null ? bid.getCreatedAt().toString() : "");
                        return m;
                    })
                    .collect(Collectors.toList());
        } catch (Exception e) {
            log.error("Failed to fetch bid history for userId: {}", userId, e);
            return List.of();
        }
    }
}
