package com.rocketcrew.pocat.domain.set.repository;

import com.rocketcrew.pocat.domain.set.entity.PokemonSet;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface PokemonSetRepository extends JpaRepository<PokemonSet, Long> {
    Optional<PokemonSet> findBySetId(String setId);
    boolean existsByName(String name);
}
