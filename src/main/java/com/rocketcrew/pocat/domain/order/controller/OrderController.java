package com.rocketcrew.pocat.domain.order.controller;

import com.rocketcrew.pocat.domain.order.dto.request.CancelOrderRequest;
import com.rocketcrew.pocat.domain.order.dto.response.OrderDetailResponse;
import com.rocketcrew.pocat.domain.order.dto.response.OrderResponse;
import com.rocketcrew.pocat.domain.order.enums.OrderStatus;
import com.rocketcrew.pocat.domain.order.service.OrderCommandService;
import com.rocketcrew.pocat.domain.order.service.OrderQueryService;
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
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/orders")
public class OrderController {

    private final OrderQueryService orderQueryService;
    private final OrderCommandService orderCommandService;

    @GetMapping("/me")
    public ResponseEntity<ApiResponseDto<PageResponseDto<OrderResponse>>> getMyOrders(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestParam(required = false) OrderStatus status,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        Page<OrderResponse> page = orderQueryService.getMyOrders(userDetails.getUserId(), status, pageable);
        return ResponseEntity.ok(ApiResponseDto.success(HttpStatus.OK, PageResponseDto.of(page, page.getContent())));
    }

    @GetMapping("/{orderUid}")
    public ResponseEntity<ApiResponseDto<OrderDetailResponse>> getOneOrder(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable String orderUid
    ) {
        return ResponseEntity.ok(ApiResponseDto.success(HttpStatus.OK, orderQueryService.getOneOrder(userDetails.getUserId(), orderUid)));
    }

    @PatchMapping("/{orderUid}/cancel")
    public ResponseEntity<ApiResponseDto<OrderResponse>> cancelOrder(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable String orderUid,
            @Valid @RequestBody CancelOrderRequest request
    ) {
        return ResponseEntity.ok(ApiResponseDto.success(HttpStatus.OK, orderCommandService.cancelOrder(userDetails.getUserId(), orderUid, request.reason())));
    }
}
