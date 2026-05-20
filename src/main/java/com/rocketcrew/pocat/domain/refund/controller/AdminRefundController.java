package com.rocketcrew.pocat.domain.refund.controller;

import com.rocketcrew.pocat.domain.refund.dto.request.RejectRefundRequest;
import com.rocketcrew.pocat.domain.refund.dto.response.AdminRefundResponse;
import com.rocketcrew.pocat.domain.refund.dto.response.RefundResponse;
import com.rocketcrew.pocat.domain.refund.entity.RefundStatus;
import com.rocketcrew.pocat.domain.refund.service.RefundCommandService;
import com.rocketcrew.pocat.domain.refund.service.RefundQueryService;
import com.rocketcrew.pocat.global.dto.ApiResponseDto;
import com.rocketcrew.pocat.global.dto.PageResponseDto;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api")
public class AdminRefundController {

    private final RefundCommandService refundCommandService;
    private final RefundQueryService refundQueryService;

    /** 7.6 전체 환불 목록 (ADMIN) */
    @GetMapping("/v1/admin/refunds")
    public ResponseEntity<ApiResponseDto<PageResponseDto<AdminRefundResponse>>> getAdminRefunds(
            @RequestParam(required = false) RefundStatus status,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        Page<AdminRefundResponse> page = refundQueryService.getAdminRefunds(status, pageable);
        return ResponseEntity.ok(ApiResponseDto.success(HttpStatus.OK,
                PageResponseDto.of(page, page.getContent())));
    }

    /** 7.4 환불 승인 (ADMIN) */
    @PatchMapping("/v1/admin/refunds/{refundId}/approve")
    public ResponseEntity<ApiResponseDto<RefundResponse>> approveRefund(
            @PathVariable Long refundId) {
        RefundResponse response = refundCommandService.approveRefund(refundId);
        return ResponseEntity.ok(ApiResponseDto.success(HttpStatus.OK, response));
    }

    /** 7.5 환불 거절 (ADMIN) */
    @PatchMapping("/v1/admin/refunds/{refundId}/reject")
    public ResponseEntity<ApiResponseDto<RefundResponse>> rejectRefund(
            @PathVariable Long refundId,
            @Valid @RequestBody RejectRefundRequest request) {
        RefundResponse response = refundCommandService.rejectRefund(refundId, request);
        return ResponseEntity.ok(ApiResponseDto.success(HttpStatus.OK, response));
    }
}
