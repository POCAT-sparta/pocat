package com.rocketcrew.pocat.domain.set.dto.response;

import com.rocketcrew.pocat.domain.set.entity.PokemonSet;

public record PokemonSetResponse(
        Long id,
        String setId,
        String name,
        String nameKo,
        Long seriesId
) {
    public static PokemonSetResponse from(PokemonSet ps) {
        return new PokemonSetResponse(
                ps.getId(), ps.getSetId(), ps.getName(), ps.getNameKo(),
                ps.getSeries() != null ? ps.getSeries().getId() : null);
    }
}
