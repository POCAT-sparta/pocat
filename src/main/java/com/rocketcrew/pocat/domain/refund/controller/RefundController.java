package com.rocketcrew.pocat.domain.refund.controller;

import com.rocketcrew.pocat.domain.refund.dto.request.CreateRefundRequest;
import com.rocketcrew.pocat.domain.refund.dto.response.RefundResponse;
import com.rocketcrew.pocat.domain.refund.entity.RefundStatus;
import com.rocketcrew.pocat.domain.refund.service.RefundCommandService;
import com.rocketcrew.pocat.domain.refund.service.RefundQueryService;
import com.rocketcrew.pocat.global.dto.ApiResponseDto;
import com.rocketcrew.pocat.global.dto.PageResponseDto;
import com.rocketcrew.pocat.global.security.CustomUserDetails;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/refunds")
public class RefundController {

    private final RefundCommandService refundCommandService;
    private final RefundQueryService refundQueryService;

    /** 7.1 환불 요청 */
    @PostMapping
    public ResponseEntity<ApiResponseDto<RefundResponse>> createRefund(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @Valid @RequestBody CreateRefundRequest request) {
        RefundResponse response = refundCommandService.createRefund(userDetails.getUserId(), request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponseDto.success(HttpStatus.CREATED, response));
    }

    /** 7.2 내 환불 내역 조회 */
    @GetMapping("/me")
    public ResponseEntity<ApiResponseDto<PageResponseDto<RefundResponse>>> getMyRefunds(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestParam(required = false) RefundStatus status,
            @PageableDefault(size = 10, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        Page<RefundResponse> page = refundQueryService.getMyRefunds(userDetails.getUserId(), status, pageable);
        return ResponseEntity.ok(ApiResponseDto.success(HttpStatus.OK,
                PageResponseDto.of(page, page.getContent())));
    }

    /** 7.3 환불 상세 조회 (본인 또는 ADMIN) */
    @GetMapping("/{refundId}")
    public ResponseEntity<ApiResponseDto<RefundResponse>> getRefund(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long refundId) {
        RefundResponse response = refundQueryService.getRefund(userDetails.getUserId(), refundId);
        return ResponseEntity.ok(ApiResponseDto.success(HttpStatus.OK, response));
    }
}
