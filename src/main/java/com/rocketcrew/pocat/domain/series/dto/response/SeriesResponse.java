package com.rocketcrew.pocat.domain.series.dto.response;

import com.rocketcrew.pocat.domain.series.entity.Series;

public record SeriesResponse(
        Long id,
        String name,
        String nameKo
) {
    public static SeriesResponse from(Series s) {
        return new SeriesResponse(s.getId(), s.getName(), s.getNameKo());
    }
}
