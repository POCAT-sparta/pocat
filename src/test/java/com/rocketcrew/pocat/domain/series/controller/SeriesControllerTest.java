package com.rocketcrew.pocat.domain.series.controller;

import com.rocketcrew.pocat.domain.series.dto.response.SeriesResponse;
import com.rocketcrew.pocat.domain.series.service.SeriesQueryService;
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
class SeriesControllerTest {

    private MockMvc mockMvc;

    @InjectMocks
    private SeriesController seriesController;

    @Mock
    private SeriesQueryService seriesQueryService;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(seriesController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Nested
    @DisplayName("GET /api/v1/series")
    class GetAll {

        @Test
        @DisplayName("200: 시리즈 전체 목록 반환")
        void success_200() throws Exception {
            given(seriesQueryService.findAll()).willReturn(List.of(
                    new SeriesResponse(1L, "Sword & Shield", "소드&실드"),
                    new SeriesResponse(2L, "Scarlet & Violet", "스칼렛&바이올렛")
            ));

            mockMvc.perform(get("/api/v1/series"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data[0].id").value(1))
                    .andExpect(jsonPath("$.data[0].name").value("Sword & Shield"))
                    .andExpect(jsonPath("$.data[0].nameKo").value("소드&실드"))
                    .andExpect(jsonPath("$.data[1].id").value(2));
        }

        @Test
        @DisplayName("200: 시리즈 없으면 빈 배열 반환")
        void success_200_empty() throws Exception {
            given(seriesQueryService.findAll()).willReturn(List.of());

            mockMvc.perform(get("/api/v1/series"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data").isEmpty());
        }
    }
}
