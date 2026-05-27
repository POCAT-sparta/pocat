package com.rocketcrew.pocat.domain.series.service;

import com.rocketcrew.pocat.domain.series.dto.request.UpsertSeriesRequest;
import com.rocketcrew.pocat.domain.series.dto.response.SeriesResponse;
import com.rocketcrew.pocat.domain.series.entity.Series;
import com.rocketcrew.pocat.domain.series.repository.SeriesRepository;
import com.rocketcrew.pocat.global.exception.common.ErrorCode;
import com.rocketcrew.pocat.global.exception.domain.SeriesException;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class SeriesCommandService {

    private final SeriesRepository seriesRepository;

    /** Card 등록/동기화 시 호출. 이미 존재하면 그대로 반환 */
    public Series findOrCreate(String name) {
        String trimmed = name != null ? name.strip() : "";
        return seriesRepository.findByName(trimmed)
                .orElseGet(() -> {
                    try {
                        return seriesRepository.save(Series.builder().name(trimmed).build());
                    } catch (DataIntegrityViolationException e) {
                        // 동시 요청으로 먼저 INSERT된 경우 재조회
                        return seriesRepository.findByName(trimmed)
                                .orElseThrow(() -> new SeriesException(ErrorCode.SERIES_NOT_FOUND));
                    }
                });
    }

    public SeriesResponse create(UpsertSeriesRequest request) {
        Series series = seriesRepository.save(
                Series.builder()
                        .name(request.name())
                        .nameKo(request.nameKo())
                        .build());
        return SeriesResponse.from(series);
    }

    public SeriesResponse updateNameKo(Long id, String nameKo) {
        Series series = seriesRepository.findById(id)
                .orElseThrow(() -> new SeriesException(ErrorCode.SERIES_NOT_FOUND));
        series.updateNameKo(nameKo);
        return SeriesResponse.from(series);
    }

    public Series findById(Long id) {
        return seriesRepository.findById(id)
                .orElseThrow(() -> new SeriesException(ErrorCode.SERIES_NOT_FOUND));
    }

    public void delete(Long id) {
        Series series = seriesRepository.findById(id)
                .orElseThrow(() -> new SeriesException(ErrorCode.SERIES_NOT_FOUND));
        seriesRepository.delete(series);
    }
}
