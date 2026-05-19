package com.rocketcrew.pocat.domain.card.controller;

import com.rocketcrew.pocat.domain.card.dto.request.CreateCardRequest;
import com.rocketcrew.pocat.domain.card.dto.response.CardResponse;
import com.rocketcrew.pocat.domain.card.service.CardCommandService;
import com.rocketcrew.pocat.domain.card.service.CardQueryService;
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
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/cards")
public class CardController {

    private final CardQueryService cardQueryService;
    private final CardCommandService cardCommandService;

    @GetMapping
    public ResponseEntity<ApiResponseDto<PageResponseDto<CardResponse>>> getCards(
            @PageableDefault(size = 10, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        Page<CardResponse> page = cardQueryService.getCards(pageable);
        List<CardResponse> content = page.getContent();
        PageResponseDto<CardResponse> pageResponse = PageResponseDto.of(page, content);
        return ResponseEntity.ok(ApiResponseDto.success(HttpStatus.OK, pageResponse));
    }

    @GetMapping("/{cardId}")
    public ResponseEntity<ApiResponseDto<CardResponse>> getCard(@PathVariable Long cardId) {
        CardResponse response = cardQueryService.getCard(cardId);
        return ResponseEntity.ok(ApiResponseDto.success(HttpStatus.OK, response));
    }

    @PostMapping
    public ResponseEntity<ApiResponseDto<CardResponse>> createCard(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestBody CreateCardRequest request) {
        CardResponse response = cardCommandService.createCard(userDetails.getUserId(), request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponseDto.success(HttpStatus.CREATED, response));
    }
}
