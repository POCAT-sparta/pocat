package com.rocketcrew.pocat.domain.series.service;

import com.rocketcrew.pocat.domain.series.dto.response.SeriesResponse;
import com.rocketcrew.pocat.domain.series.entity.Series;
import com.rocketcrew.pocat.domain.series.repository.SeriesRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SeriesQueryService {

    private final SeriesRepository seriesRepository;

    /**
     * 정규화된 한글·영문 시리즈 별칭 → 영문 시리즈명 인메모리 캐시.
     * DomainDataSeeder가 nameKo 갱신 후 rebuildCache()를 호출해 갱신한다.
     */
    private final Map<String, String> translationCache = new ConcurrentHashMap<>();

    @PostConstruct
    public void buildCache() {
        rebuildCache();
    }

    /** DomainDataSeeder의 enrichSeriesNameKo() 완료 직후 호출해 캐시를 최신화한다. */
    public void rebuildCache() {
        Map<String, String> fresh = new ConcurrentHashMap<>();
        seriesRepository.findAll().forEach(s -> {
            // 영문 이름 자체도 캐시에 등록 (영문 직접 입력 호환)
            fresh.put(normalize(s.getName()), s.getName());
            if (s.getNameKo() != null) {
                Arrays.stream(s.getNameKo().split("\\s+"))
                      .forEach(alias -> fresh.put(normalize(alias), s.getName()));
            }
        });
        translationCache.clear();
        translationCache.putAll(fresh);
    }

    public List<SeriesResponse> findAll() {
        return seriesRepository.findAll().stream()
                .map(SeriesResponse::from).toList();
    }

    /**
     * 한글(또는 영문) 시리즈명 → DB 영문 시리즈명.
     * ES term 필터용. 매핑 없으면 원본 반환 (영문 직접 입력 호환).
     */
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
