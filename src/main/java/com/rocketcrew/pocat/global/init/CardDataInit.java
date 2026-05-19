package com.rocketcrew.pocat.global.init;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rocketcrew.pocat.domain.card.entity.Card;
import com.rocketcrew.pocat.domain.card.entity.enums.CardCategory;
import com.rocketcrew.pocat.domain.card.entity.enums.CardGrade;
import com.rocketcrew.pocat.domain.card.entity.enums.CardSource;
import com.rocketcrew.pocat.domain.card.entity.enums.CardStatus;
import com.rocketcrew.pocat.domain.card.repository.CardRepository;
import com.rocketcrew.pocat.domain.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class CardDataInit implements ApplicationRunner {

    private static final String TCGDEX_SET_URL = "https://api.tcgdex.net/v2/en/sets/swsh3";
    private static final String TCGDEX_CARD_URL = "https://api.tcgdex.net/v2/en/cards/";
    private static final int MAX_CARDS = 50;
    // 더미 데이터이므로 테스트용 다양한 등급 순환 할당
    private static final CardGrade[] GRADES = CardGrade.values();

    private final CardRepository cardRepository;
    private final UserRepository userRepository;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional
    public void run(ApplicationArguments args) throws Exception {
        if (cardRepository.count() > 0) {
            log.info("[CardDataInit] 카드 데이터가 이미 존재합니다. 초기화를 건너뜁니다.");
            return;
        }

        Long userId = userRepository.findFirstBy()
                .map(user -> user.getId())
                .orElse(null);

        if (userId == null) {
            log.warn("[CardDataInit] 유저가 없어 카드 초기화를 건너뜁니다.");
            return;
        }

        try {
            RestTemplate restTemplate = createRestTemplate();

            // 세트 조회 → 카드 ID 목록 + 시리즈명 획득
            String setResponse = restTemplate.getForObject(TCGDEX_SET_URL, String.class);
            JsonNode setRoot = objectMapper.readTree(setResponse);

            String seriesName = setRoot.path("serie").path("name").asText("Sword & Shield");
            String setName = setRoot.path("name").asText("Darkness Ablaze");
            JsonNode cardNodes = setRoot.path("cards");

            List<Card> cardList = new ArrayList<>();
            int count = 0;

            for (JsonNode cardNode : cardNodes) {
                if (count >= MAX_CARDS) break;

                String tcgdexId = cardNode.path("id").asText();

                // 개별 카드 조회 → rarity, category 획득
                try {
                    String cardResponse = restTemplate.getForObject(TCGDEX_CARD_URL + tcgdexId, String.class);
                    JsonNode cardRoot = objectMapper.readTree(cardResponse);

                    String name = cardRoot.path("name").asText();
                    String localId = cardRoot.path("localId").asText();
                    String imageUrl = cardRoot.path("image").asText("") + "/high.webp";
                    String rarity = cardRoot.path("rarity").asText("");
                    CardCategory category = parseCategory(cardRoot.path("category").asText(""));
                    CardGrade grade = GRADES[count % GRADES.length];

                    Card card = Card.builder()
                            .userId(userId)
                            .tcgdexId(tcgdexId)
                            .name(name)
                            .series(seriesName)
                            .setName(setName)
                            .cardNumber(localId)
                            .rarity(rarity.isEmpty() ? null : rarity)
                            .category(category)
                            .grade(grade)
                            .imageUrl(imageUrl)
                            .source(CardSource.TCGDEX)
                            .status(CardStatus.ACTIVE)
                            .build();

                    cardList.add(card);
                    count++;

                } catch (Exception e) {
                    log.warn("[CardDataInit] 카드 조회 실패 ({}): {}", tcgdexId, e.getMessage());
                }
            }

            cardRepository.saveAll(cardList);
            log.info("[CardDataInit] 카드 {}개 초기화 완료", cardList.size());

        } catch (Exception e) {
            // 선택적 초기화이므로 실패해도 애플리케이션 시작은 계속 진행
            log.error("[CardDataInit] 카드 초기화 실패: {}", e.getMessage());
        }
    }

    private RestTemplate createRestTemplate() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(5000);
        factory.setReadTimeout(10000);
        return new RestTemplate(factory);
    }

    private CardCategory parseCategory(String category) {
        if (category == null || category.isBlank()) return null;
        return switch (category.toUpperCase()) {
            case "POKEMON" -> CardCategory.POKEMON;
            case "TRAINER", "TRAINERS" -> CardCategory.TRAINERS;
            case "ENERGY" -> CardCategory.ENERGY;
            default -> null;
        };
    }
}
