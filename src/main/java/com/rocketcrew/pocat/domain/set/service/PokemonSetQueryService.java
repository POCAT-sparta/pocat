package com.rocketcrew.pocat.domain.set.service;

import com.rocketcrew.pocat.domain.set.dto.response.PokemonSetResponse;
import com.rocketcrew.pocat.domain.set.entity.PokemonSet;
import com.rocketcrew.pocat.domain.set.repository.PokemonSetRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PokemonSetQueryService {

    private final PokemonSetRepository pokemonSetRepository;

    public List<PokemonSetResponse> findAll() {
        return pokemonSetRepository.findAll().stream()
                .map(PokemonSetResponse::from).toList();
    }

    /** 한글(또는 영문) 확장팩명 → DB 영문 set name. ES term 필터용 */
    public String translate(String input) {
        if (input == null || input.isBlank()) return input;
        String normalized = normalize(input);
        return pokemonSetRepository.findAll().stream()
                .filter(ps -> {
                    if (normalize(ps.getName()).equals(normalized)) return true;
                    return ps.getNameKo() != null &&
                           Arrays.stream(ps.getNameKo().split("\\s+"))
                                 .anyMatch(alias -> normalize(alias).equals(normalized));
                })
                .findFirst()
                .map(PokemonSet::getName)
                .orElse(input);
    }

    private static String normalize(String s) {
        return s.replaceAll("[^가-힣a-zA-Z0-9]", "");
    }
}
