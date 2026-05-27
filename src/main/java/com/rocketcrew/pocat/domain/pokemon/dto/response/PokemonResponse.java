package com.rocketcrew.pocat.domain.pokemon.dto.response;

import com.rocketcrew.pocat.domain.pokemon.entity.Pokemon;

public record PokemonResponse(Long id, String name, String nameKo) {
    public static PokemonResponse from(Pokemon p) {
        return new PokemonResponse(p.getId(), p.getName(), p.getNameKo());
    }
}
