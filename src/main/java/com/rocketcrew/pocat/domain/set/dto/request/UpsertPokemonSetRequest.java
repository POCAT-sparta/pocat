package com.rocketcrew.pocat.domain.set.dto.request;

import jakarta.validation.constraints.NotBlank;

public record UpsertPokemonSetRequest(
        @NotBlank String setId,
        @NotBlank String name,
        String nameKo,
        Long seriesId
) {}
