package com.rocketcrew.pocat.domain.card.util;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.yaml.snakeyaml.Yaml;

import java.io.InputStream;
import java.util.Collections;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Component
public class PokemonNameDictionary {

    private final Map<String, String> enToKo;

    public PokemonNameDictionary() {
        Map<String, String> loaded = Collections.emptyMap();
        try (InputStream is = getClass().getResourceAsStream("/pokemon-names.yml")) {
            if (is != null) {
                Map<String, Map<String, String>> root = new Yaml().load(is);
                Map<String, String> koToEn = root.getOrDefault("names", Collections.emptyMap());
                loaded = koToEn.entrySet().stream()
                        .collect(Collectors.toMap(Map.Entry::getValue, Map.Entry::getKey));
                log.info("[PokemonNameDictionary] {}개 포켓몬 이름 로드 완료", loaded.size());
            }
        } catch (Exception e) {
            log.warn("[PokemonNameDictionary] pokemon-names.yml 로드 실패: {}", e.getMessage());
        }
        this.enToKo = loaded;
    }

    // "Mega Charizard X ex" → "리자몽" (카드명을 단어 단위로 분리해서 사전 조회)
    public String findKoreanName(String englishCardName) {
        if (englishCardName == null) return null;
        for (String word : englishCardName.split("\\s+")) {
            String ko = enToKo.get(word);
            if (ko != null) return ko;
        }
        return null;
    }
}
