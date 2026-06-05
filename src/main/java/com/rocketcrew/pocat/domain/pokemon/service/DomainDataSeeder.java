package com.rocketcrew.pocat.domain.pokemon.service;

import com.rocketcrew.pocat.domain.card.entity.Card;
import com.rocketcrew.pocat.domain.card.repository.CardRepository;
import com.rocketcrew.pocat.domain.pokemon.entity.Pokemon;
import com.rocketcrew.pocat.domain.series.repository.SeriesRepository;
import com.rocketcrew.pocat.domain.series.service.SeriesQueryService;
import com.rocketcrew.pocat.domain.set.entity.PokemonSet;
import com.rocketcrew.pocat.domain.set.repository.PokemonSetRepository;
import com.rocketcrew.pocat.domain.set.service.PokemonSetQueryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.yaml.snakeyaml.Yaml;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Slf4j
@Component
@RequiredArgsConstructor
public class DomainDataSeeder {

    private final SeriesRepository seriesRepository;
    private final PokemonSetRepository pokemonSetRepository;
    private final PokemonCommandService pokemonCommandService;
    private final CardRepository cardRepository;
    private final SeriesQueryService seriesQueryService;
    private final PokemonSetQueryService pokemonSetQueryService;

    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void seed() {
        enrichSeriesNameKo();
        seriesQueryService.rebuildCache();

        enrichPokemonSetNameKo();
        pokemonSetQueryService.rebuildCache();

        linkPokemonToCards();
    }

    /** series-names.yml → Series.nameKo 업데이트 */
    private void enrichSeriesNameKo() {
        Map<String, List<String>> enToKoList = loadYamlAsEnToKoList("/series-names.yml");
        enToKoList.forEach((en, koList) -> {
            String joined = String.join(" ", koList);
            seriesRepository.findByName(en).ifPresent(s -> {
                s.updateNameKo(joined);
                log.info("[Seeder] Series nameKo 업데이트: {} → {}", en, joined);
            });
        });
    }

    /** set-names.yml → PokemonSet.nameKo 업데이트 */
    private void enrichPokemonSetNameKo() {
        Map<String, List<String>> enToKoList = loadYamlAsEnToKoList("/set-names.yml");
        if (enToKoList.isEmpty()) return;
        List<PokemonSet> allSets = pokemonSetRepository.findAll();
        enToKoList.forEach((en, koList) -> {
            String joined = String.join(" ", koList);
            allSets.stream()
                    .filter(ps -> ps.getName().equals(en))
                    .forEach(ps -> {
                        ps.updateNameKo(joined);
                        log.info("[Seeder] PokemonSet nameKo 업데이트: {} → {}", en, joined);
                    });
        });
    }

    /**
     * POKEMON 카드 중 pokemon_id 미설정 카드에 pokemon 연결.
     * 커서(keyset) 페이지네이션으로 처리 — pokemon_id를 채우면서 오프셋이 틀어지는 문제 방지.
     */
    private void linkPokemonToCards() {
        final int PAGE_SIZE = 100;
        long lastId = 0L;
        int linked = 0;
        int total = 0;
        List<Card> batch;
        do {
            batch = cardRepository.findPokemonCardsWithNullPokemonAfter(lastId, PageRequest.of(0, PAGE_SIZE));
            for (Card card : batch) {
                lastId = card.getId(); // 커서 전진 (미연결 카드도 포함 — 재처리 방지)
                total++;
                Optional<Pokemon> pokemon = pokemonCommandService.findOrCreateForCardName(card.getName());
                if (pokemon.isPresent()) {
                    card.linkPokemon(pokemon.get());
                    linked++;
                }
            }
        } while (!batch.isEmpty());
        log.info("[Seeder] {}개 카드 pokemon 연결 완료 (전체 미연결: {}개)", linked, total);
    }

    /**
     * YAML 파일에서 ko → en 맵을 로드하고, en → [ko, ko2, ...] 역방향 맵으로 변환.
     * YAML 구조: names: {한글명: 영문명, ...}
     */
    private Map<String, List<String>> loadYamlAsEnToKoList(String resource) {
        Map<String, List<String>> enToKoList = new LinkedHashMap<>();
        try (InputStream is = getClass().getResourceAsStream(resource)) {
            if (is == null) {
                log.debug("[Seeder] {} 파일 없음 (스킵)", resource);
                return enToKoList;
            }
            Map<Object, Object> root = new Yaml().load(is);
            Object namesObj = root.get("names");
            if (namesObj instanceof Map<?, ?> rawMap) {
                for (Map.Entry<?, ?> entry : rawMap.entrySet()) {
                    String ko = String.valueOf(entry.getKey());
                    String en = String.valueOf(entry.getValue());
                    enToKoList.computeIfAbsent(en, k -> new ArrayList<>()).add(ko);
                }
            }
        } catch (Exception e) {
            log.warn("[Seeder] {} 로드 실패: {}", resource, e.getMessage());
        }
        return enToKoList;
    }
}
