package com.rocketcrew.pocat.domain.refund.controller;

import com.rocketcrew.pocat.domain.refund.dto.request.CreateRefundRequest;
import com.rocketcrew.pocat.domain.refund.dto.request.RejectRefundRequest;
import com.rocketcrew.pocat.domain.refund.dto.response.RefundResponse;
import com.rocketcrew.pocat.domain.refund.service.RefundService;
import com.rocketcrew.pocat.global.dto.ApiResponseDto;
import com.rocketcrew.pocat.global.dto.PageResponseDto;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/refunds")
public class RefundController {

    private final RefundService refundService;

    @GetMapping
    public ResponseEntity<ApiResponseDto<PageResponseDto<RefundResponse>>> getRefunds(
            @PageableDefault(size = 10, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        Page<RefundResponse> page = refundService.getRefunds(pageable);
        List<RefundResponse> content = page.getContent();
        PageResponseDto<RefundResponse> pageResponse = PageResponseDto.of(page, content);
        return ResponseEntity.ok(ApiResponseDto.success(HttpStatus.OK, pageResponse));
    }

    @GetMapping("/{refundId}")
    public ResponseEntity<ApiResponseDto<RefundResponse>> getRefund(@PathVariable Long refundId) {
        RefundResponse response = refundService.getRefund(refundId);
        return ResponseEntity.ok(ApiResponseDto.success(HttpStatus.OK, response));
    }

    @PostMapping
    public ResponseEntity<ApiResponseDto<RefundResponse>> createRefund(
            @RequestBody CreateRefundRequest request) {
        RefundResponse response = refundService.createRefund(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponseDto.success(HttpStatus.CREATED, response));
    }

    @PutMapping("/{refundId}/approve")
    public ResponseEntity<ApiResponseDto<RefundResponse>> approveRefund(@PathVariable Long refundId) {
        RefundResponse response = refundService.approveRefund(refundId);
        return ResponseEntity.ok(ApiResponseDto.success(HttpStatus.OK, response));
    }

    @PutMapping("/{refundId}/reject")
    public ResponseEntity<ApiResponseDto<RefundResponse>> rejectRefund(
            @PathVariable Long refundId,
            @RequestBody RejectRefundRequest request) {
        RefundResponse response = refundService.rejectRefund(refundId, request);
        return ResponseEntity.ok(ApiResponseDto.success(HttpStatus.OK, response));
    }
}
