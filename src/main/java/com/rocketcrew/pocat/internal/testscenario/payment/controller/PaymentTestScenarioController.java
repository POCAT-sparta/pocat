package com.rocketcrew.pocat.internal.testscenario.payment.controller;

import io.swagger.v3.oas.annotations.Hidden;
import com.rocketcrew.pocat.global.dto.ApiResponseDto;
import com.rocketcrew.pocat.internal.testscenario.payment.dto.AutoPaymentFailureInjectionResponse;
import com.rocketcrew.pocat.internal.testscenario.payment.service.PaymentTestScenarioService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Validated
@Hidden
@RestController
@RequestMapping("/internal/test/payments")
@RequiredArgsConstructor
public class PaymentTestScenarioController {

    private final PaymentTestScenarioService paymentTestScenarioService;

    @PostMapping("/{orderUid}/auto-fail")
    public ResponseEntity<ApiResponseDto<AutoPaymentFailureInjectionResponse>> injectAutoPaymentFailure(
            @PathVariable String orderUid) {
        AutoPaymentFailureInjectionResponse response =
                paymentTestScenarioService.injectAutoPaymentFailure(orderUid);
        return ResponseEntity.ok(ApiResponseDto.success(HttpStatus.OK, response));
    }
}
