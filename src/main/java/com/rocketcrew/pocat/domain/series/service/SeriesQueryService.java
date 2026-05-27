package com.rocketcrew.pocat.domain.series.service;

import com.rocketcrew.pocat.domain.series.dto.response.SeriesResponse;
import com.rocketcrew.pocat.domain.series.entity.Series;
import com.rocketcrew.pocat.domain.series.repository.SeriesRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SeriesQueryService {

    private final SeriesRepository seriesRepository;

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
        // 영문 그대로 통과
        if (seriesRepository.existsByName(input)) return input;
        // 한글 → nameKo 공백 분리 별칭 매칭
        String normalized = normalize(input);
        return seriesRepository.findAll().stream()
                .filter(s -> s.getNameKo() != null &&
                             Arrays.stream(s.getNameKo().split("\\s+"))
                                   .anyMatch(alias -> normalize(alias).equals(normalized)))
                .findFirst()
                .map(Series::getName)
                .orElse(input);
    }

    private static String normalize(String s) {
        return s.replaceAll("[^가-힣a-zA-Z0-9]", "");
    }
}
