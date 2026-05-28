package com.rocketcrew.pocat.domain.pokemon.repository;

import com.rocketcrew.pocat.domain.pokemon.entity.Pokemon;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface PokemonRepository extends JpaRepository<Pokemon, Long> {
    Optional<Pokemon> findByName(String name);
    boolean existsByName(String name);
}
