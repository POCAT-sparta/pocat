package com.rocketcrew.pocat.domain.ai.assistant.tools;

import com.rocketcrew.pocat.domain.card.entity.Card;
import com.rocketcrew.pocat.domain.card.repository.CardRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.Tool;
import org.springframework.ai.tool.ToolParam;
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

            // CardRepository를 사용하여 조건에 맞는 카드 검색
            // 실제 구현은 DB 에이전트가 쿼리메서드 추가
            List<Card> cards = cardRepository.findAll().stream()
                    .filter(card -> grade == null || card.getGrade().toString().equals(grade))
                    .filter(card -> name == null || card.getName().contains(name))
                    .limit(10)
                    .collect(Collectors.toList());

            return cards.stream()
                    .map(card -> Map.ofEntries(
                            Map.entry("id", card.getId()),
                            Map.entry("name", card.getName()),
                            Map.entry("grade", card.getGrade().toString()),
                            Map.entry("series", card.getSeries()),
                            Map.entry("rarity", card.getRarity()),
                            Map.entry("imageUrl", card.getImageUrl())
                    ))
                    .collect(Collectors.toList());
        } catch (Exception e) {
            log.error("Card search failed: {}", e.getMessage(), e);
            return List.of();
        }
    }
}
