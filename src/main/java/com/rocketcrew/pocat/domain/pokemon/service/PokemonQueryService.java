package com.rocketcrew.pocat.domain.pokemon.service;

import com.rocketcrew.pocat.domain.pokemon.dto.response.PokemonResponse;
import com.rocketcrew.pocat.domain.pokemon.repository.PokemonRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PokemonQueryService {

    private final PokemonRepository pokemonRepository;

    public List<PokemonResponse> findAll() {
        return pokemonRepository.findAll().stream().map(PokemonResponse::from).toList();
    }
}
