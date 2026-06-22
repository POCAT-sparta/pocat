package com.rocketcrew.pocat.domain.series.controller;

import io.swagger.v3.oas.annotations.tags.Tag;
import com.rocketcrew.pocat.domain.series.dto.request.UpsertSeriesRequest;
import com.rocketcrew.pocat.domain.series.dto.response.SeriesResponse;
import com.rocketcrew.pocat.domain.series.service.SeriesCommandService;
import com.rocketcrew.pocat.domain.series.service.SeriesQueryService;
import com.rocketcrew.pocat.global.dto.ApiResponseDto;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "관리자 - 시리즈", description = "시리즈 관리")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/admin/series")
@PreAuthorize("hasRole('ADMIN')")
public class AdminSeriesController {

    private final SeriesQueryService seriesQueryService;
    private final SeriesCommandService seriesCommandService;

    @GetMapping
    public ResponseEntity<ApiResponseDto<List<SeriesResponse>>> getAll() {
        return ResponseEntity.ok(ApiResponseDto.success(HttpStatus.OK, seriesQueryService.findAll()));
    }

    @PostMapping
    public ResponseEntity<ApiResponseDto<SeriesResponse>> create(
            @Valid @RequestBody UpsertSeriesRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponseDto.success(HttpStatus.CREATED, seriesCommandService.create(request)));
    }

    @PatchMapping("/{id}/name-ko")
    public ResponseEntity<ApiResponseDto<SeriesResponse>> updateNameKo(
            @PathVariable Long id, @RequestParam String nameKo) {
        return ResponseEntity.ok(ApiResponseDto.success(HttpStatus.OK, seriesCommandService.updateNameKo(id, nameKo)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponseDto<Void>> delete(@PathVariable Long id) {
        seriesCommandService.delete(id);
        return ResponseEntity.ok(ApiResponseDto.successWithNoContent());
    }
}
