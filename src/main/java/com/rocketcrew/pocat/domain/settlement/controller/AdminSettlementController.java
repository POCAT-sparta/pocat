package com.rocketcrew.pocat.domain.settlement.controller;

import com.rocketcrew.pocat.domain.settlement.dto.request.AdminSettlementSearchCondition;
import com.rocketcrew.pocat.domain.settlement.dto.response.AdminSettlementResponse;
import com.rocketcrew.pocat.domain.settlement.dto.response.SettlementCompleteResponse;
import com.rocketcrew.pocat.domain.settlement.service.AdminSettlementCommandService;
import com.rocketcrew.pocat.domain.settlement.service.AdminSettlementQueryService;
import com.rocketcrew.pocat.global.dto.ApiResponseDto;
import com.rocketcrew.pocat.global.dto.PageResponseDto;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
@RequestMapping("/api")
public class AdminSettlementController {

    private final AdminSettlementCommandService adminSettlementCommandService;
    private final AdminSettlementQueryService adminSettlementQueryService;

    @GetMapping("/v1/admin/settlements")
    public ResponseEntity<ApiResponseDto<PageResponseDto<AdminSettlementResponse>>> getAdminSettlements(
            @ModelAttribute AdminSettlementSearchCondition condition,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        Page<AdminSettlementResponse> page = adminSettlementQueryService.getAdminSettlements(condition, pageable);
        return ResponseEntity.ok(ApiResponseDto.success(HttpStatus.OK, PageResponseDto.of(page, page.getContent())));
    }

    @PatchMapping("/v1/admin/settlements/{settlementUid}/complete")
    public ResponseEntity<ApiResponseDto<SettlementCompleteResponse>> completeSettlement(
            @PathVariable String settlementUid
    ) {
        SettlementCompleteResponse response = adminSettlementCommandService.completeSettlement(settlementUid);
        return ResponseEntity.ok(ApiResponseDto.success(HttpStatus.OK, response));
    }
}
