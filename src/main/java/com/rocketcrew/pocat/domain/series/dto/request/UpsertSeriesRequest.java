package com.rocketcrew.pocat.domain.series.dto.request;

import jakarta.validation.constraints.NotBlank;

public record UpsertSeriesRequest(
        @NotBlank String name,
        String nameKo
) {}
