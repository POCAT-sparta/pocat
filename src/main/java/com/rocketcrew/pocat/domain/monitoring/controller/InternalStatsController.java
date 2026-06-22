package com.rocketcrew.pocat.domain.monitoring.controller;

import io.swagger.v3.oas.annotations.Hidden;
import com.rocketcrew.pocat.domain.monitoring.dto.DailyStatsResponse;
import com.rocketcrew.pocat.domain.monitoring.service.InternalStatsService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Hidden
@RestController
@RequestMapping("/internal/stats")
@RequiredArgsConstructor
public class InternalStatsController {

    private final InternalStatsService statsService;

    @GetMapping("/daily")
    public ResponseEntity<DailyStatsResponse> getDailyStats() {
        return ResponseEntity.ok(statsService.getDailyStats());
    }
}
