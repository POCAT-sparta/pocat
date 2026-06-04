package com.rocketcrew.pocat.domain.card.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rocketcrew.pocat.domain.card.entity.Card;
import com.rocketcrew.pocat.domain.card.entity.enums.CardCategory;
import com.rocketcrew.pocat.domain.card.entity.enums.CardGrade;
import com.rocketcrew.pocat.domain.card.entity.enums.CardSource;
import com.rocketcrew.pocat.domain.card.entity.enums.CardStatus;
import com.rocketcrew.pocat.domain.card.repository.CardRepository;
import com.rocketcrew.pocat.domain.pokemon.entity.Pokemon;
import com.rocketcrew.pocat.domain.pokemon.service.PokemonCommandService;
import com.rocketcrew.pocat.domain.series.entity.Series;
import com.rocketcrew.pocat.domain.series.service.SeriesCommandService;
import com.rocketcrew.pocat.domain.set.entity.PokemonSet;
import com.rocketcrew.pocat.domain.set.service.PokemonSetCommandService;
import com.rocketcrew.pocat.domain.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

@Slf4j
@Service
@RequiredArgsConstructor
public class CardSyncService {

    private final CardCommandService cardCommandService;

    private static final String TCGDEX_SETS_URL = "https://api.tcgdex.net/v2/en/sets";
    private static final String TCGDEX_SET_URL  = "https://api.tcgdex.net/v2/en/sets/";
    private static final String TCGDEX_CARD_URL = "https://api.tcgdex.net/v2/en/cards/";

    //TODO : Spring Batch 적용

    // TCGdex는 등급 정보를 제공하지 않으므로 순환 할당 (더미 데이터 성격)
    private static final CardGrade[] GRADES = CardGrade.values();

    private final CardRepository cardRepository;
    private final UserRepository userRepository;
    private final ObjectMapper objectMapper;
    private final SeriesCommandService seriesCommandService;
    private final PokemonSetCommandService pokemonSetCommandService;
    private final PokemonCommandService pokemonCommandService;

    /**
     * 카드 세트 전체를 동기화한다.
     * 배치 시스템에서 호출. 더 이상 스케줄러로 자동 실행하지 않음.
     */
    public void syncAll() {
        log.info("[CARD_SYNC] 주간 전체 동기화 시작");

        // 스케줄러 실행 시 HTTP 컨텍스트가 없으므로 DB에서 첫 번째 유저 ID 사용
        // 사용자가 없으면 FK 오류 또는 잘못된 소유자 저장을 막기 위해 동기화 중단
        Long adminUserId = userRepository.findFirstByOrderByIdAsc()
                .map(user -> user.getId())
                .orElse(null);

        if (adminUserId == null) {
            log.error("[CARD_SYNC] 등록된 사용자가 없어 동기화를 중단합니다. 최소 1명의 사용자가 필요합니다.");
            return;
        }

        RestTemplate restTemplate = createRestTemplate();
        int totalSynced = 0;

        try {
            String setsJson = restTemplate.getForObject(TCGDEX_SETS_URL, String.class);
            JsonNode setsArray = objectMapper.readTree(setsJson);

            for (JsonNode setNode : setsArray) {
                String setId = setNode.path("id").asText();
                try {
                    totalSynced += syncSet(restTemplate, adminUserId, setId, totalSynced);
                } catch (Exception e) {
                    log.warn("[CARD_SYNC] 세트 동기화 실패 setId={}: {}", setId, e.getMessage());
                }
            }

        } catch (Exception e) {
            log.error("[CARD_SYNC] 전체 동기화 실패: {}", e.getMessage(), e);
        }

        log.info("[CARD_SYNC] 주간 전체 동기화 완료 newCardCount={}", totalSynced);
    }

    /**
     * 단일 세트를 동기화한다.
     * 이미 DB에 존재하는 tcgdexId는 스킵하며, 신규 카드만 저장한다.
     */
    private int syncSet(RestTemplate restTemplate, Long adminUserId, String setId, int offset) throws Exception {
        String setJson = restTemplate.getForObject(TCGDEX_SET_URL + setId, String.class);
        JsonNode setRoot = objectMapper.readTree(setJson);

        String seriesName = setRoot.path("serie").path("name").asText("");
        String setName    = setRoot.path("name").asText("");
        JsonNode cardNodes = setRoot.path("cards");

        int synced = 0;
        for (JsonNode cardNode : cardNodes) {
            String tcgdexId = cardNode.path("id").asText();

            // DB에 이미 존재하면 스킵
            if (cardRepository.existsByTcgdexId(tcgdexId)) {
                continue;
            }

            try {
                String cardJson = restTemplate.getForObject(TCGDEX_CARD_URL + tcgdexId, String.class);
                JsonNode cardRoot = objectMapper.readTree(cardJson);

                String name      = cardRoot.path("name").asText();
                String localId   = cardRoot.path("localId").asText();
                String imageBase = cardRoot.path("image").asText("");
                String imageUrl  = imageBase.isEmpty() ? null : imageBase + "/high.webp";
                String rarity    = cardRoot.path("rarity").asText("");
                CardCategory category = parseCategory(cardRoot.path("category").asText(""));
                CardGrade grade = GRADES[(offset + synced) % GRADES.length];

                Series seriesEntity = seriesCommandService.findOrCreate(seriesName);
                PokemonSet pokemonSetEntity = pokemonSetCommandService.findOrCreate(setId, setName, seriesEntity);
                Pokemon pokemonEntity = (category == CardCategory.POKEMON)
                        ? pokemonCommandService.findOrCreateForCardName(name).orElse(null)
                        : null;

                Card card = Card.builder()
                        .userId(adminUserId)
                        .tcgdexId(tcgdexId)
                        .name(name)
                        .series(seriesEntity)
                        .pokemonSet(pokemonSetEntity)
                        .pokemon(pokemonEntity)
                        .cardNumber(localId)
                        .rarity(rarity.isEmpty() ? "UNKNOWN" : rarity)
                        .category(category)
                        .grade(grade)
                        .imageUrl(imageUrl)
                        .source(CardSource.TCGDEX)
                        .status(CardStatus.ACTIVE)
                        .build();

                try {
                    Card saved = cardRepository.save(card);
                    cardCommandService.indexCard(saved);
                    synced++;
                } catch (DataIntegrityViolationException e) {
                    // 동시 요청 시 race condition 방어
                    log.warn("[CARD_SYNC] 중복 카드 스킵(race) tcgdexId={}: {}", tcgdexId, e.getMessage());
                }

            } catch (Exception e) {
                log.warn("[CARD_SYNC] 카드 처리 실패 tcgdexId={}: {}", tcgdexId, e.getMessage());
            }
        }

        return synced;
    }

    private RestTemplate createRestTemplate() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(5_000);
        factory.setReadTimeout(10_000);
        return new RestTemplate(factory);
    }

    private CardCategory parseCategory(String category) {
        if (category == null || category.isBlank()) return CardCategory.UNKNOWN;
        return switch (category.toUpperCase()) {
            case "POKEMON"             -> CardCategory.POKEMON;
            case "TRAINER", "TRAINERS" -> CardCategory.TRAINERS;
            case "ENERGY"              -> CardCategory.ENERGY;
            default                    -> CardCategory.UNKNOWN;
        };
    }
}
