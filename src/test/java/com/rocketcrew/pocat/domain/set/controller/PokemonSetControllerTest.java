package com.rocketcrew.pocat.domain.set.controller;

import com.rocketcrew.pocat.domain.set.dto.response.PokemonSetResponse;
import com.rocketcrew.pocat.domain.set.service.PokemonSetQueryService;
import com.rocketcrew.pocat.global.exception.common.GlobalExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class PokemonSetControllerTest {

    private MockMvc mockMvc;

    @InjectMocks
    private PokemonSetController pokemonSetController;

    @Mock
    private PokemonSetQueryService pokemonSetQueryService;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(pokemonSetController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Nested
    @DisplayName("GET /api/v1/sets")
    class GetAll {

        @Test
        @DisplayName("200: seriesId 없으면 전체 목록 반환")
        void success_200_all() throws Exception {
            given(pokemonSetQueryService.findAll()).willReturn(List.of(
                    new PokemonSetResponse(1L, "swsh1", "Sword & Shield Base", "소드&실드 베이스", 1L),
                    new PokemonSetResponse(2L, "sv1", "Scarlet & Violet Base", "스칼렛&바이올렛 베이스", 2L)
            ));

            mockMvc.perform(get("/api/v1/sets"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data[0].id").value(1))
                    .andExpect(jsonPath("$.data[0].setId").value("swsh1"))
                    .andExpect(jsonPath("$.data[0].seriesId").value(1))
                    .andExpect(jsonPath("$.data[1].id").value(2));
        }

        @Test
        @DisplayName("200: seriesId 있으면 해당 시리즈 세트만 반환")
        void success_200_filtered() throws Exception {
            given(pokemonSetQueryService.findBySeriesId(1L)).willReturn(List.of(
                    new PokemonSetResponse(1L, "swsh1", "Sword & Shield Base", "소드&실드 베이스", 1L)
            ));

            mockMvc.perform(get("/api/v1/sets").param("seriesId", "1"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.length()").value(1))
                    .andExpect(jsonPath("$.data[0].seriesId").value(1));
        }

        @Test
        @DisplayName("200: 해당 시리즈에 세트 없으면 빈 배열 반환")
        void success_200_filtered_empty() throws Exception {
            given(pokemonSetQueryService.findBySeriesId(99L)).willReturn(List.of());

            mockMvc.perform(get("/api/v1/sets").param("seriesId", "99"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data").isEmpty());
        }
    }
}
