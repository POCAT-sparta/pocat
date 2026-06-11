package com.rocketcrew.pocat.global.controller;

import com.rocketcrew.pocat.domain.auction.service.AuctionEsMigrationService;
import com.rocketcrew.pocat.domain.card.service.CardEsAliasReindexService;
import com.rocketcrew.pocat.domain.card.service.CardEsMigrationService;
import com.rocketcrew.pocat.global.dto.ApiResponseDto;
import com.rocketcrew.pocat.global.dto.EsMigrationResponse;
import com.rocketcrew.pocat.global.dto.EsReindexResponse;
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
    private final CardEsAliasReindexService cardEsAliasReindexService;

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

    /**
     * ES Index Alias 초기 세팅 (최초 1회 실행).
     * cards 직접 인덱스를 cards_v1으로 복사 후 alias "cards"로 교체한다.
     * alias가 이미 존재하면 스킵.
     */
    @PostMapping("/v1/admin/es-alias-setup")
    public ResponseEntity<ApiResponseDto<EsReindexResponse>> aliasSetup() {
        EsReindexResponse response = cardEsAliasReindexService.setupAlias();
        return ResponseEntity.ok(ApiResponseDto.success(HttpStatus.OK, response));
    }

    /**
     * 무중단 재인덱싱: cards_vN → cards_v(N+1), alias 원자 교체.
     * 매핑 변경 또는 전체 재색인이 필요할 때 실행한다.
     * es-alias-setup이 선행되어야 한다.
     */
    @PostMapping("/v1/admin/es-reindex")
    public ResponseEntity<ApiResponseDto<EsReindexResponse>> reindex() {
        EsReindexResponse response = cardEsAliasReindexService.reindex();
        return ResponseEntity.ok(ApiResponseDto.success(HttpStatus.OK, response));
    }
}
