package com.rocketcrew.pocat.domain.ai.assistant.tools;

import com.rocketcrew.pocat.domain.card.entity.Card;
import com.rocketcrew.pocat.domain.card.entity.enums.CardGrade;
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
 * 카드 검색 Tool for Spring AI Tool Calling.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CardSearchTool {

    private final CardRepository cardRepository;

    @Tool(description = "카드를 등급, 최대 가격, 이름으로 검색합니다. 경매 목록에 등재된 활성 카드만 반환합니다.")
    public List<Map<String, Object>> searchCards(
            @ToolParam(description = "카드 등급: PSA_10, PSA_9, BGS_10") String grade,
            @ToolParam(description = "최대 가격 (원 단위), 생략 가능") Long maxPrice,
            @ToolParam(description = "카드 이름 (부분 일치 검색 가능)") String name
    ) {
        try {
            log.info("Searching cards with grade={}, maxPrice={}, name={}", grade, maxPrice, name);

            CardGrade cardGrade = null;
            if (grade != null && !grade.isBlank()) {
                try { cardGrade = CardGrade.valueOf(grade); } catch (IllegalArgumentException ignored) {}
            }
            List<Card> cards = cardRepository.findActiveCardsByNameContainingAndGrade(
                    name, cardGrade, PageRequest.of(0, 10));

            return cards.stream()
                    .map(card -> {
                        Map<String, Object> m = new java.util.HashMap<>();
                        m.put("id", card.getId());
                        m.put("name", card.getName());
                        m.put("grade", card.getGrade().toString());
                        m.put("series", card.getSeries());
                        m.put("rarity", card.getRarity());
                        m.put("imageUrl", card.getImageUrl());
                        return m;
                    })
                    .collect(Collectors.toList());
        } catch (Exception e) {
            log.error("Card search failed: {}", e.getMessage(), e);
            return List.of();
        }
    }
}
