package com.rocketcrew.pocat.global.init;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rocketcrew.pocat.domain.card.entity.Card;
import com.rocketcrew.pocat.domain.card.entity.enums.CardCategory;
import com.rocketcrew.pocat.domain.card.entity.enums.CardGrade;
import com.rocketcrew.pocat.domain.card.entity.enums.CardSource;
import com.rocketcrew.pocat.domain.card.entity.enums.CardStatus;
import com.rocketcrew.pocat.domain.card.repository.CardRepository;
import com.rocketcrew.pocat.domain.user.entity.User;
import com.rocketcrew.pocat.domain.user.enums.UserRole;
import com.rocketcrew.pocat.domain.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
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

    private static final String ADMIN_EMAIL = "admin@test.com";
    private static final String ADMIN_PASSWORD = "test1234";
    private static final String ADMIN_NICKNAME = "admin";

    private final CardRepository cardRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final ObjectMapper objectMapper;

    @Override
    public void run(ApplicationArguments args) throws Exception {
        Long userId = userRepository.findFirstByOrderByIdAsc()
                .map(user -> user.getId())
                .orElseGet(() -> {
                    User admin = User.builder()
                            .email(ADMIN_EMAIL)
                            .password(passwordEncoder.encode(ADMIN_PASSWORD))
                            .nickname(ADMIN_NICKNAME)
                            .userRole(UserRole.ADMIN)
                            .build();
                    User saved = userRepository.save(admin);
                    log.info("[CardDataInit] 어드민 유저 생성 완료 (email: {})", ADMIN_EMAIL);
                    return saved.getId();
                });

        try {
            RestTemplate restTemplate = createRestTemplate();

            // 세트 조회 → 카드 ID 목록 + 시리즈명 획득
            String setResponse = restTemplate.getForObject(TCGDEX_SET_URL, String.class);
            JsonNode setRoot = objectMapper.readTree(setResponse);

            String setId = setRoot.path("id").asText("");
            if (setId.isBlank()) {
                log.error("[CardDataInit] TCGdex 응답에 세트 ID가 없어 초기화를 중단합니다.");
                return;
            }
            String seriesName = setRoot.path("serie").path("name").asText("Sword & Shield");
            String setName = setRoot.path("name").asText("Darkness Ablaze");
            JsonNode cardNodes = setRoot.path("cards");

            List<Card> cardList = new ArrayList<>();
            int attempted = 0; // 시도 횟수 (MAX_CARDS 상한 기준)
            int saved = 0;     // 성공 횟수 (등급 순환 기준)

            for (JsonNode cardNode : cardNodes) {
                if (attempted >= MAX_CARDS) break;
                attempted++;

                String tcgdexId = cardNode.path("id").asText();

                if (cardRepository.existsByTcgdexId(tcgdexId)) {
                    log.debug("[CardDataInit] 이미 존재하는 카드 스킵: {}", tcgdexId);
                    continue;
                }

                // 개별 카드 조회 → rarity, category 획득
                try {
                    String cardResponse = restTemplate.getForObject(TCGDEX_CARD_URL + tcgdexId, String.class);
                    JsonNode cardRoot = objectMapper.readTree(cardResponse);

                    String name = cardRoot.path("name").asText();
                    String localId = cardRoot.path("localId").asText();
                    String imageBase = cardRoot.path("image").asText("");
                    String imageUrl = imageBase.isEmpty() ? null : imageBase + "/high.webp";
                    String rarity = cardRoot.path("rarity").asText("");
                    CardCategory category = parseCategory(cardRoot.path("category").asText(""));
                    CardGrade grade = GRADES[saved % GRADES.length];

                    Card card = Card.builder()
                            .userId(userId)
                            .tcgdexId(tcgdexId)
                            .name(name)
                            .series(seriesName)
                            .setId(setId)
                            .setName(setName)
                            .cardNumber(localId)
                            .rarity(rarity.isEmpty() ? "UNKNOWN" : rarity)
                            .category(category)
                            .grade(grade)
                            .imageUrl(imageUrl)
                            .source(CardSource.TCGDEX)
                            .status(CardStatus.ACTIVE)
                            .build();

                    cardList.add(card);
                    saved++;

                } catch (Exception e) {
                    log.warn("[CardDataInit] 카드 조회 실패 ({}): {}", tcgdexId, e.getMessage());
                }
            }

            if (!cardList.isEmpty()) {
                saveCards(cardList);
                log.info("[CardDataInit] 카드 {}개 초기화 완료 (시도: {})", cardList.size(), attempted);
            } else {
                log.info("[CardDataInit] 신규 카드 없음. 초기화 건너뜁니다.");
            }

        } catch (Exception e) {
            // 선택적 초기화이므로 실패해도 애플리케이션 시작은 계속 진행
            log.error("[CardDataInit] 카드 초기화 실패: {}", e.getMessage());
        }
    }

    public void saveCards(List<Card> cards) {
        for (Card card : cards) {
            try {
                cardRepository.save(card);
            } catch (DataIntegrityViolationException e) {
                log.warn("[CardDataInit] 중복 카드 감지, 삽입 건너뜀: {}", card.getTcgdexId());
            }
        }
    }

    private RestTemplate createRestTemplate() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(5000);
        factory.setReadTimeout(10000);
        return new RestTemplate(factory);
    }

    private CardCategory parseCategory(String category) {
        if (category == null || category.isBlank()) return CardCategory.UNKNOWN;
        return switch (category.toUpperCase()) {
            case "POKEMON" -> CardCategory.POKEMON;
            case "TRAINER", "TRAINERS" -> CardCategory.TRAINERS;
            case "ENERGY" -> CardCategory.ENERGY;
            default -> CardCategory.UNKNOWN;
        };
    }
}
