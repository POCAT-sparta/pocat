package com.rocketcrew.pocat.internal.testscenario.auction.controller;

import io.swagger.v3.oas.annotations.Hidden;
import com.rocketcrew.pocat.global.dto.ApiResponseDto;
import com.rocketcrew.pocat.internal.testscenario.auction.dto.AuctionCloseWithoutAutoPaymentResponse;
import com.rocketcrew.pocat.internal.testscenario.auction.dto.AuctionExpirationInjectionResponse;
import com.rocketcrew.pocat.internal.testscenario.auction.dto.AuctionExpirationScheduleResponse;
import com.rocketcrew.pocat.internal.testscenario.auction.service.AuctionTestScenarioService;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Validated
@Hidden
@RestController
@RequestMapping("/internal/test/auctions")
@RequiredArgsConstructor
public class AuctionTestScenarioController {

    private final AuctionTestScenarioService auctionTestScenarioService;

    @PostMapping("/{auctionId}/expire-now")
    public ResponseEntity<ApiResponseDto<AuctionExpirationInjectionResponse>> makeExpired(
            @PathVariable @Positive Long auctionId) {
        AuctionExpirationInjectionResponse response = auctionTestScenarioService.makeExpired(auctionId);
        return ResponseEntity.ok(ApiResponseDto.success(HttpStatus.OK, response));
    }

    @PostMapping("/{auctionId}/expire-in")
    public ResponseEntity<ApiResponseDto<AuctionExpirationScheduleResponse>> scheduleExpiration(
            @PathVariable @Positive Long auctionId,
            @RequestParam(defaultValue = "600") long seconds) {
        AuctionExpirationScheduleResponse response = auctionTestScenarioService.scheduleExpiration(auctionId, seconds);
        return ResponseEntity.ok(ApiResponseDto.success(HttpStatus.OK, response));
    }

    @PostMapping("/{auctionId}/close-expired-without-auto-payment")
    public ResponseEntity<ApiResponseDto<AuctionCloseWithoutAutoPaymentResponse>> closeExpiredWithoutAutoPayment(
            @PathVariable @Positive Long auctionId) {
        AuctionCloseWithoutAutoPaymentResponse response =
                auctionTestScenarioService.closeExpiredWithoutAutoPayment(auctionId);
        return ResponseEntity.ok(ApiResponseDto.success(HttpStatus.OK, response));
    }
}
