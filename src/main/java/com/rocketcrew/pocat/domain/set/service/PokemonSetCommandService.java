package com.rocketcrew.pocat.domain.set.service;

import com.rocketcrew.pocat.domain.series.entity.Series;
import com.rocketcrew.pocat.domain.set.dto.request.UpsertPokemonSetRequest;
import com.rocketcrew.pocat.domain.set.dto.response.PokemonSetResponse;
import com.rocketcrew.pocat.domain.set.entity.PokemonSet;
import com.rocketcrew.pocat.domain.set.repository.PokemonSetRepository;
import com.rocketcrew.pocat.global.exception.common.ErrorCode;
import com.rocketcrew.pocat.global.exception.domain.PokemonSetException;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class PokemonSetCommandService {

    private final PokemonSetRepository pokemonSetRepository;

    /** Card 등록/동기화 시 호출. setId 기준으로 find-or-create */
    public PokemonSet findOrCreate(String setId, String setName, Series series) {
        String trimmedId = setId != null ? setId.strip() : null;
        if (trimmedId == null || trimmedId.isBlank()) {
            throw new IllegalArgumentException("setId must not be blank");
        }
        return pokemonSetRepository.findBySetId(trimmedId)
                .orElseGet(() -> {
                    try {
                        return pokemonSetRepository.save(
                                PokemonSet.builder()
                                        .setId(trimmedId)
                                        .name(setName != null ? setName.strip() : setName)
                                        .series(series)
                                        .build());
                    } catch (DataIntegrityViolationException e) {
                        // 동시 요청으로 먼저 INSERT된 경우 재조회
                        return pokemonSetRepository.findBySetId(trimmedId)
                                .orElseThrow(() -> e); // 재조회도 실패하면 원래 예외 원인 보존
                    }
                });
    }

    public PokemonSetResponse create(UpsertPokemonSetRequest request, Series series) {
        String trimmedId = request.setId() != null ? request.setId().strip() : null;
        try {
            PokemonSet ps = pokemonSetRepository.save(
                    PokemonSet.builder()
                            .setId(trimmedId)
                            .name(request.name() != null ? request.name().strip() : null)
                            .nameKo(request.nameKo())
                            .series(series)
                            .build());
            return PokemonSetResponse.from(ps);
        } catch (DataIntegrityViolationException e) {
            // 동시 요청 또는 중복 setId — 기존 엔티티 반환
            return pokemonSetRepository.findBySetId(trimmedId)
                    .map(PokemonSetResponse::from)
                    .orElseThrow(() -> e); // 재조회도 실패하면 원래 예외 원인 보존
        }
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
