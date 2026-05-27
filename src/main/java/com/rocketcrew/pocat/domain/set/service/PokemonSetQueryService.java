package com.rocketcrew.pocat.domain.set.service;

import com.rocketcrew.pocat.domain.set.dto.response.PokemonSetResponse;
import com.rocketcrew.pocat.domain.set.entity.PokemonSet;
import com.rocketcrew.pocat.domain.set.repository.PokemonSetRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PokemonSetQueryService {

    private final PokemonSetRepository pokemonSetRepository;

    /**
     * 정규화된 한글·영문 확장팩 별칭 → 영문 set name 캐시 (volatile 참조 교체로 원자적 갱신).
     * DomainDataSeeder가 nameKo 갱신 후 rebuildCache()를 호출해 갱신한다.
     */
    private volatile Map<String, String> translationCache = Map.of();

    @PostConstruct
    public void buildCache() {
        rebuildCache();
    }

    /** DomainDataSeeder의 enrichPokemonSetNameKo() 완료 직후 호출해 캐시를 최신화한다. */
    public void rebuildCache() {
        Map<String, String> fresh = new HashMap<>();
        pokemonSetRepository.findAll().forEach(ps -> {
            // 영문 이름 자체도 캐시에 등록 (영문 직접 입력 호환)
            fresh.put(normalize(ps.getName()), ps.getName());
            if (ps.getNameKo() != null) {
                Arrays.stream(ps.getNameKo().split("\\s+"))
                      .forEach(alias -> fresh.put(normalize(alias), ps.getName()));
            }
        });
        translationCache = Map.copyOf(fresh); // 참조 교체로 원자적 갱신 — clear+putAll 사이 빈 캐시 노출 없음
    }

    public List<PokemonSetResponse> findAll() {
        return pokemonSetRepository.findAll().stream()
                .map(PokemonSetResponse::from).toList();
    }

    /** 한글(또는 영문) 확장팩명 → DB 영문 set name. ES term 필터용 */
    public String translate(String input) {
        if (input == null || input.isBlank()) return input;
        if (translationCache.isEmpty()) rebuildCache();
        String en = translationCache.get(normalize(input));
        return en != null ? en : input;
    }

    private static String normalize(String s) {
        return s.toLowerCase(Locale.ROOT).replaceAll("[^가-힣a-z0-9]", "");
    }
}
