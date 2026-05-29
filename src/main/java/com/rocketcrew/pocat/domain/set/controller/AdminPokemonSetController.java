package com.rocketcrew.pocat.domain.set.controller;

import com.rocketcrew.pocat.domain.series.entity.Series;
import com.rocketcrew.pocat.domain.series.service.SeriesCommandService;
import com.rocketcrew.pocat.domain.set.dto.request.UpsertPokemonSetRequest;
import com.rocketcrew.pocat.domain.set.dto.response.PokemonSetResponse;
import com.rocketcrew.pocat.domain.set.service.PokemonSetCommandService;
import com.rocketcrew.pocat.domain.set.service.PokemonSetQueryService;
import com.rocketcrew.pocat.global.dto.ApiResponseDto;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/admin/sets")
@PreAuthorize("hasRole('ADMIN')")
public class AdminPokemonSetController {

    private final PokemonSetQueryService pokemonSetQueryService;
    private final PokemonSetCommandService pokemonSetCommandService;
    private final SeriesCommandService seriesCommandService;

    @GetMapping
    public ResponseEntity<ApiResponseDto<List<PokemonSetResponse>>> getAll() {
        return ResponseEntity.ok(ApiResponseDto.success(HttpStatus.OK, pokemonSetQueryService.findAll()));
    }

    @PostMapping
    public ResponseEntity<ApiResponseDto<PokemonSetResponse>> create(
            @Valid @RequestBody UpsertPokemonSetRequest request) {
        Series series = request.seriesId() != null
                ? seriesCommandService.findById(request.seriesId())
                : null;
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponseDto.success(HttpStatus.CREATED,
                        pokemonSetCommandService.create(request, series)));
    }

    @PatchMapping("/{id}/name-ko")
    public ResponseEntity<ApiResponseDto<PokemonSetResponse>> updateNameKo(
            @PathVariable Long id, @RequestParam String nameKo) {
        return ResponseEntity.ok(ApiResponseDto.success(HttpStatus.OK,
                pokemonSetCommandService.updateNameKo(id, nameKo)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponseDto<Void>> delete(@PathVariable Long id) {
        pokemonSetCommandService.delete(id);
        return ResponseEntity.ok(ApiResponseDto.successWithNoContent());
    }
}
