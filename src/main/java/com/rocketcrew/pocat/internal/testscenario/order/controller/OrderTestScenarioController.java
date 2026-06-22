package com.rocketcrew.pocat.internal.testscenario.order.controller;

import io.swagger.v3.oas.annotations.Hidden;
import com.rocketcrew.pocat.global.dto.ApiResponseDto;
import com.rocketcrew.pocat.internal.testscenario.order.dto.OrderPaymentDeadlineInjectionResponse;
import com.rocketcrew.pocat.internal.testscenario.order.dto.OrderPaymentDeadlineScheduleResponse;
import com.rocketcrew.pocat.internal.testscenario.order.service.OrderTestScenarioService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@Hidden
@RestController
@RequestMapping("/internal/test/orders")
@RequiredArgsConstructor
public class OrderTestScenarioController {

    private final OrderTestScenarioService orderTestScenarioService;

    @PostMapping("/{orderUid}/expire-payment-window")
    public ResponseEntity<ApiResponseDto<OrderPaymentDeadlineInjectionResponse>> expirePaymentWindowNow(
            @PathVariable String orderUid) {
        OrderPaymentDeadlineInjectionResponse response =
                orderTestScenarioService.expirePaymentWindowNow(orderUid);
        return ResponseEntity.ok(ApiResponseDto.success(HttpStatus.OK, response));
    }

    @PostMapping("/{orderUid}/expire-payment-window-in")
    public ResponseEntity<ApiResponseDto<OrderPaymentDeadlineScheduleResponse>> schedulePaymentWindowExpiration(
            @PathVariable String orderUid,
            @RequestParam(defaultValue = "600") long seconds) {
        OrderPaymentDeadlineScheduleResponse response =
                orderTestScenarioService.schedulePaymentWindowExpiration(orderUid, seconds);
        return ResponseEntity.ok(ApiResponseDto.success(HttpStatus.OK, response));
    }
}
