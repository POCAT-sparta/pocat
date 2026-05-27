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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.yaml.snakeyaml.Yaml;

import java.io.InputStream;
import java.util.*;

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

        seedPokemon();
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

    /** pokemon-names.yml → Pokemon 테이블 시드 (이미 있으면 스킵) */
    private void seedPokemon() {
        if (pokemonCommandService.getCacheSize() > 0) {
            log.info("[Seeder] Pokemon 이미 {}개 로드됨, 시드 스킵", pokemonCommandService.getCacheSize());
            return;
        }
        try (InputStream is = getClass().getResourceAsStream("/pokemon-names.yml")) {
            if (is == null) {
                log.warn("[Seeder] pokemon-names.yml 파일을 찾을 수 없음");
                return;
            }
            Map<String, Map<String, String>> root = new Yaml().load(is);
            Map<String, String> koToEn = root.getOrDefault("names", Collections.emptyMap());
            // koToEn: {한글명 → 영문명} — 같은 EN에 여러 KO가 있을 수 있으므로 first-win
            Map<String, String> enToKo = new LinkedHashMap<>();
            koToEn.forEach((ko, en) -> enToKo.putIfAbsent(en, ko));
            enToKo.forEach((en, ko) -> pokemonCommandService.findOrCreate(en, ko));
            log.info("[Seeder] Pokemon {}개 시드 완료", enToKo.size());
        } catch (Exception e) {
            log.warn("[Seeder] pokemon-names.yml 로드 실패: {}", e.getMessage());
        }
        // 시드 후 캐시 재빌드
        pokemonCommandService.buildCache();
    }

    /** POKEMON 카드 중 pokemon_id 미설정 카드에 pokemon 연결 — 페이지 단위 처리 */
    private void linkPokemonToCards() {
        final int PAGE_SIZE = 100;
        int page = 0;
        int linked = 0;
        int total = 0;
        Page<Card> result;
        do {
            result = cardRepository.findPokemonCardsWithNullPokemon(PageRequest.of(page++, PAGE_SIZE));
            for (Card card : result.getContent()) {
                total++;
                Optional<Pokemon> pokemon = pokemonCommandService.findOrCreateForCardName(card.getName());
                if (pokemon.isPresent()) {
                    card.linkPokemon(pokemon.get());
                    linked++;
                }
            }
        } while (result.hasNext());
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
