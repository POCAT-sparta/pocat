package com.rocketcrew.pocat.domain.pokemon.dto.request;

import jakarta.validation.constraints.NotBlank;

public record UpsertPokemonRequest(@NotBlank String name, String nameKo) {}
