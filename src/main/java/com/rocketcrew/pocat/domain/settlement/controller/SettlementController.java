package com.rocketcrew.pocat.domain.settlement.controller;

import com.rocketcrew.pocat.domain.settlement.dto.response.SettlementResponse;
import com.rocketcrew.pocat.domain.settlement.service.SettlementQueryService;
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
@RequestMapping("/api")
public class SettlementController {

    private final SettlementQueryService settlementQueryService;

    @GetMapping("/v1/settlements/me")
    public ResponseEntity<ApiResponseDto<PageResponseDto<SettlementResponse>>> getSettlements(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        Page<SettlementResponse> page = settlementQueryService.getSettlements(userDetails.getUserId(), pageable);
        List<SettlementResponse> content = page.getContent();
        PageResponseDto<SettlementResponse> pageResponse = PageResponseDto.of(page, content);
        return ResponseEntity.ok(ApiResponseDto.success(HttpStatus.OK, pageResponse));
    }

    @GetMapping("/v1/settlements/{settlementUid}")
    public ResponseEntity<ApiResponseDto<SettlementResponse>> getOneSettlement(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable String settlementUid
    ) {
        SettlementResponse response = settlementQueryService.getOneSettlement(userDetails.getUserId(), settlementUid);
        return ResponseEntity.ok(ApiResponseDto.success(HttpStatus.OK, response));
    }
}
