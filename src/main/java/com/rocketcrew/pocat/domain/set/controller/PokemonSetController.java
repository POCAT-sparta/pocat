package com.rocketcrew.pocat.domain.set.controller;

import io.swagger.v3.oas.annotations.tags.Tag;
import com.rocketcrew.pocat.domain.set.dto.response.PokemonSetResponse;
import com.rocketcrew.pocat.domain.set.service.PokemonSetQueryService;
import com.rocketcrew.pocat.global.dto.ApiResponseDto;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "카드셋", description = "포켓몬 카드 세트 조회")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/sets")
public class PokemonSetController {

    private final PokemonSetQueryService pokemonSetQueryService;

    @GetMapping
    public ResponseEntity<ApiResponseDto<List<PokemonSetResponse>>> getAll(
            @RequestParam(required = false) Long seriesId) {
        List<PokemonSetResponse> result = seriesId != null
                ? pokemonSetQueryService.findBySeriesId(seriesId)
                : pokemonSetQueryService.findAll();
        return ResponseEntity.ok(ApiResponseDto.success(HttpStatus.OK, result));
    }
}
