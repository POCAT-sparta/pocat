package com.rocketcrew.pocat.domain.pokemon.service;

import com.rocketcrew.pocat.domain.pokemon.entity.Pokemon;
import com.rocketcrew.pocat.domain.pokemon.repository.PokemonRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class PokemonCommandServiceTest {

    @InjectMocks PokemonCommandService pokemonCommandService;
    @Mock PokemonRepository pokemonRepository;

    @BeforeEach
    void setup() {
        Pokemon charizard = Pokemon.builder().name("Charizard").nameKo("리자몽").build();
        ReflectionTestUtils.setField(charizard, "id", 1L);
        Pokemon mrMime = Pokemon.builder().name("Mr. Mime").nameKo("마임맨").build();
        ReflectionTestUtils.setField(mrMime, "id", 2L);
        given(pokemonRepository.findAll()).willReturn(List.of(charizard, mrMime));
        // @PostConstruct 수동 호출 (@InjectMocks는 @PostConstruct를 자동 실행하지 않음)
        pokemonCommandService.buildCache();
    }

    @Test
    @DisplayName("슬라이딩 윈도우: 'Charizard ex'에서 Charizard 매칭")
    void findOrCreateForCardName_sliding() {
        Optional<Pokemon> result = pokemonCommandService.findOrCreateForCardName("Charizard ex");
        assertThat(result).isPresent();
        assertThat(result.get().getName()).isEqualTo("Charizard");
    }

    @Test
    @DisplayName("슬라이딩 윈도우: 'Mr. Mime V' 다단어 포켓몬 매칭")
    void findOrCreateForCardName_multiword() {
        Optional<Pokemon> result = pokemonCommandService.findOrCreateForCardName("Mr. Mime V");
        assertThat(result).isPresent();
        assertThat(result.get().getName()).isEqualTo("Mr. Mime");
    }

    @Test
    @DisplayName("슬라이딩 윈도우: 매칭 없으면 empty 반환")
    void findOrCreateForCardName_noMatch() {
        Optional<Pokemon> result = pokemonCommandService.findOrCreateForCardName("Professor's Research");
        assertThat(result).isEmpty();
    }
}
