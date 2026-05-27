package com.rocketcrew.pocat.domain.set.service;

import com.rocketcrew.pocat.domain.series.entity.Series;
import com.rocketcrew.pocat.domain.set.dto.request.UpsertPokemonSetRequest;
import com.rocketcrew.pocat.domain.set.dto.response.PokemonSetResponse;
import com.rocketcrew.pocat.domain.set.entity.PokemonSet;
import com.rocketcrew.pocat.domain.set.repository.PokemonSetRepository;
import com.rocketcrew.pocat.global.exception.common.ErrorCode;
import com.rocketcrew.pocat.global.exception.domain.PokemonSetException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class PokemonSetCommandService {

    private final PokemonSetRepository pokemonSetRepository;

    /** Card 등록/동기화 시 호출. setId 기준으로 find-or-create */
    public PokemonSet findOrCreate(String setId, String setName, Series series) {
        return pokemonSetRepository.findBySetId(setId)
                .orElseGet(() -> pokemonSetRepository.save(
                        PokemonSet.builder()
                                .setId(setId)
                                .name(setName)
                                .series(series)
                                .build()));
    }

    public PokemonSetResponse create(UpsertPokemonSetRequest request, Series series) {
        PokemonSet ps = pokemonSetRepository.save(
                PokemonSet.builder()
                        .setId(request.setId())
                        .name(request.name())
                        .nameKo(request.nameKo())
                        .series(series)
                        .build());
        return PokemonSetResponse.from(ps);
    }

    public PokemonSetResponse updateNameKo(Long id, String nameKo) {
        PokemonSet ps = pokemonSetRepository.findById(id)
                .orElseThrow(() -> new PokemonSetException(ErrorCode.POKEMON_SET_NOT_FOUND));
        ps.updateNameKo(nameKo);
        return PokemonSetResponse.from(ps);
    }

    public void delete(Long id) {
        PokemonSet ps = pokemonSetRepository.findById(id)
                .orElseThrow(() -> new PokemonSetException(ErrorCode.POKEMON_SET_NOT_FOUND));
        pokemonSetRepository.delete(ps);
    }
}
