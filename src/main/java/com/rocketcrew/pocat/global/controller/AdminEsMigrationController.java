package com.rocketcrew.pocat.global.controller;

import com.rocketcrew.pocat.domain.auction.service.AuctionEsMigrationService;
import com.rocketcrew.pocat.domain.card.service.CardEsMigrationService;
import com.rocketcrew.pocat.global.dto.ApiResponseDto;
import com.rocketcrew.pocat.global.dto.EsMigrationResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api")
@PreAuthorize("hasRole('ADMIN')")
public class AdminEsMigrationController {

    private final CardEsMigrationService cardEsMigrationService;
    private final AuctionEsMigrationService auctionEsMigrationService;

    /**
     * DB에 있는 카드(ACTIVE)와 경매(ACTIVE/ENDED/NO_BIDDER)를 ES에 일괄 인덱싱.
     * 최초 구동 시 또는 ES 인덱스 재구성이 필요할 때 1회 실행한다.
     */
    @PostMapping("/v1/admin/es-migrate")
    public ResponseEntity<ApiResponseDto<EsMigrationResponse>> migrateAll() {
        int cardsMigrated = cardEsMigrationService.migrateAll();
        int auctionsMigrated = auctionEsMigrationService.migrateAll();
        EsMigrationResponse response = new EsMigrationResponse(cardsMigrated, auctionsMigrated);
        return ResponseEntity.ok(ApiResponseDto.success(HttpStatus.OK, response));
    }
}
