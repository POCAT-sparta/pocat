package com.rocketcrew.pocat.domain.order.controller;

import com.rocketcrew.pocat.domain.order.dto.request.AdminOrderSearchCondition;
import com.rocketcrew.pocat.domain.order.dto.response.AdminOrderResponse;
import com.rocketcrew.pocat.domain.order.service.AdminOrderQueryService;
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
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
@RequestMapping("/api/v1/admin/orders")
public class AdminOrderController {

    private final AdminOrderQueryService adminOrderQueryService;

    @GetMapping
    public ResponseEntity<ApiResponseDto<PageResponseDto<AdminOrderResponse>>> getAdminOrders(
            @ModelAttribute AdminOrderSearchCondition condition,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        Page<AdminOrderResponse> page = adminOrderQueryService.getAdminOrders(condition, pageable);
        return ResponseEntity.ok(ApiResponseDto.success(HttpStatus.OK, PageResponseDto.of(page, page.getContent())));
    }
}
