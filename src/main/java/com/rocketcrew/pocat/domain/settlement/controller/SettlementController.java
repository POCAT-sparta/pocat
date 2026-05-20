package com.rocketcrew.pocat.domain.settlement.controller;

import com.rocketcrew.pocat.domain.settlement.dto.response.SettlementResponse;
import com.rocketcrew.pocat.domain.settlement.service.SettlementService;
import com.rocketcrew.pocat.global.dto.ApiResponseDto;
import com.rocketcrew.pocat.global.dto.PageResponseDto;
import com.rocketcrew.pocat.global.security.CustomUserDetails;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/settlements")
public class SettlementController {

    private final SettlementService settlementService;

    @GetMapping
    public ResponseEntity<ApiResponseDto<PageResponseDto<SettlementResponse>>> getSettlements(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PageableDefault(size = 10, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        Page<SettlementResponse> page = settlementService.getSettlements(userDetails.getUserId(), pageable);
        List<SettlementResponse> content = page.getContent();
        PageResponseDto<SettlementResponse> pageResponse = PageResponseDto.of(page, content);
        return ResponseEntity.ok(ApiResponseDto.success(HttpStatus.OK, pageResponse));
    }

    @GetMapping("/{settlementId}")
    public ResponseEntity<ApiResponseDto<SettlementResponse>> getSettlement(@PathVariable Long settlementId) {
        SettlementResponse response = settlementService.getSettlement(settlementId);
        return ResponseEntity.ok(ApiResponseDto.success(HttpStatus.OK, response));
    }
}
