package com.rocketcrew.pocat.domain.pokemon.controller;

import com.rocketcrew.pocat.domain.pokemon.dto.request.UpsertPokemonRequest;
import com.rocketcrew.pocat.domain.pokemon.dto.response.PokemonResponse;
import com.rocketcrew.pocat.domain.pokemon.entity.Pokemon;
import com.rocketcrew.pocat.domain.pokemon.service.PokemonCommandService;
import com.rocketcrew.pocat.domain.pokemon.service.PokemonQueryService;
import com.rocketcrew.pocat.global.dto.ApiResponseDto;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/admin/pokemon")
public class AdminPokemonController {

    private final PokemonQueryService pokemonQueryService;
    private final PokemonCommandService pokemonCommandService;

    @GetMapping
    public ResponseEntity<ApiResponseDto<List<PokemonResponse>>> getAll() {
        return ResponseEntity.ok(ApiResponseDto.success(HttpStatus.OK, pokemonQueryService.findAll()));
    }

    @PostMapping
    public ResponseEntity<ApiResponseDto<PokemonResponse>> create(
            @Valid @RequestBody UpsertPokemonRequest request) {
        Pokemon saved = pokemonCommandService.findOrCreate(request.name(), request.nameKo());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponseDto.success(HttpStatus.CREATED, PokemonResponse.from(saved)));
    }

    @PatchMapping("/{id}/name-ko")
    public ResponseEntity<ApiResponseDto<PokemonResponse>> updateNameKo(
            @PathVariable Long id, @RequestParam String nameKo) {
        return ResponseEntity.ok(ApiResponseDto.success(HttpStatus.OK,
                pokemonCommandService.updateNameKo(id, nameKo)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponseDto<Void>> delete(@PathVariable Long id) {
        pokemonCommandService.delete(id);
        return ResponseEntity.ok(ApiResponseDto.successWithNoContent());
    }
}
