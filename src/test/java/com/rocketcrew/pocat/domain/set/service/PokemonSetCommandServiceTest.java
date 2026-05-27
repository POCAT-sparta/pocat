package com.rocketcrew.pocat.domain.set.service;

import com.rocketcrew.pocat.domain.series.entity.Series;
import com.rocketcrew.pocat.domain.set.entity.PokemonSet;
import com.rocketcrew.pocat.domain.set.repository.PokemonSetRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class PokemonSetCommandServiceTest {

    @InjectMocks PokemonSetCommandService pokemonSetCommandService;
    @Mock PokemonSetRepository pokemonSetRepository;

    @Test
    @DisplayName("findOrCreate: 존재하는 setId면 저장 없이 반환")
    void findOrCreate_existing() {
        PokemonSet existing = PokemonSet.builder().setId("swsh5").name("Rebel Clash").build();
        ReflectionTestUtils.setField(existing, "id", 1L);
        given(pokemonSetRepository.findBySetId("swsh5")).willReturn(Optional.of(existing));

        Series series = Series.builder().name("Sword & Shield").build();
        PokemonSet result = pokemonSetCommandService.findOrCreate("swsh5", "Rebel Clash", series);

        assertThat(result.getSetId()).isEqualTo("swsh5");
        verify(pokemonSetRepository, never()).save(any());
    }

    @Test
    @DisplayName("findOrCreate: 없는 setId면 INSERT 후 반환")
    void findOrCreate_new() {
        PokemonSet saved = PokemonSet.builder().setId("sv1").name("Scarlet & Violet").build();
        ReflectionTestUtils.setField(saved, "id", 2L);
        given(pokemonSetRepository.findBySetId("sv1")).willReturn(Optional.empty());
        given(pokemonSetRepository.save(any())).willReturn(saved);

        Series series = Series.builder().name("Scarlet & Violet").build();
        PokemonSet result = pokemonSetCommandService.findOrCreate("sv1", "Scarlet & Violet", series);

        assertThat(result.getName()).isEqualTo("Scarlet & Violet");
        verify(pokemonSetRepository).save(any());
    }
}
