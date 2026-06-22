package com.rocketcrew.pocat.domain.series.controller;

import io.swagger.v3.oas.annotations.tags.Tag;
import com.rocketcrew.pocat.domain.series.dto.response.SeriesResponse;
import com.rocketcrew.pocat.domain.series.service.SeriesQueryService;
import com.rocketcrew.pocat.global.dto.ApiResponseDto;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "시리즈", description = "포켓몬 카드 시리즈 조회")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/series")
public class SeriesController {

    private final SeriesQueryService seriesQueryService;

    @GetMapping
    public ResponseEntity<ApiResponseDto<List<SeriesResponse>>> getAll() {
        return ResponseEntity.ok(ApiResponseDto.success(HttpStatus.OK, seriesQueryService.findAll()));
    }
}
