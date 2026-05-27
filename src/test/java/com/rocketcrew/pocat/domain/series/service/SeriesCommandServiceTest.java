package com.rocketcrew.pocat.domain.series.service;

import com.rocketcrew.pocat.domain.series.entity.Series;
import com.rocketcrew.pocat.domain.series.repository.SeriesRepository;
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
class SeriesCommandServiceTest {

    @InjectMocks SeriesCommandService seriesCommandService;
    @Mock SeriesRepository seriesRepository;

    @Test
    @DisplayName("findOrCreate: 존재하는 시리즈면 저장 없이 반환")
    void findOrCreate_existing() {
        Series existing = Series.builder().name("Sword & Shield").build();
        ReflectionTestUtils.setField(existing, "id", 1L);
        given(seriesRepository.findByName("Sword & Shield")).willReturn(Optional.of(existing));

        Series result = seriesCommandService.findOrCreate("Sword & Shield");

        assertThat(result.getName()).isEqualTo("Sword & Shield");
        verify(seriesRepository, never()).save(any());
    }

    @Test
    @DisplayName("findOrCreate: 없는 시리즈면 INSERT 후 반환")
    void findOrCreate_new() {
        Series saved = Series.builder().name("New Series").build();
        ReflectionTestUtils.setField(saved, "id", 2L);
        given(seriesRepository.findByName("New Series")).willReturn(Optional.empty());
        given(seriesRepository.save(any())).willReturn(saved);

        Series result = seriesCommandService.findOrCreate("New Series");

        assertThat(result.getName()).isEqualTo("New Series");
        verify(seriesRepository).save(any());
    }
}
