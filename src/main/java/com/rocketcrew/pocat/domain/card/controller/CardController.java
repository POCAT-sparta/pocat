package com.rocketcrew.pocat.domain.card.controller;

import com.rocketcrew.pocat.domain.card.dto.request.CreateCardRequest;
import com.rocketcrew.pocat.domain.card.dto.request.UpdateCardRequest;
import com.rocketcrew.pocat.domain.card.dto.response.CardResponse;
import com.rocketcrew.pocat.domain.card.service.CardService;
import com.rocketcrew.pocat.domain.order.dto.response.CardAveragePriceResponse;
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
@RequestMapping("/api/v1/cards")
public class CardController {

    private final CardService cardService;

    @GetMapping
    public ResponseEntity<ApiResponseDto<PageResponseDto<CardResponse>>> getCards(
            @PageableDefault(size = 10, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        Page<CardResponse> page = cardService.getCards(pageable);
        List<CardResponse> content = page.getContent();
        PageResponseDto<CardResponse> pageResponse = PageResponseDto.of(page, content);
        return ResponseEntity.ok(ApiResponseDto.success(HttpStatus.OK, pageResponse));
    }

    @GetMapping("/{cardId}")
    public ResponseEntity<ApiResponseDto<CardResponse>> getCard(@PathVariable Long cardId) {
        CardResponse response = cardService.getCard(cardId);
        return ResponseEntity.ok(ApiResponseDto.success(HttpStatus.OK, response));
    }

    @PostMapping
    public ResponseEntity<ApiResponseDto<CardResponse>> createCard(
            @RequestParam Long userId,
            @RequestBody CreateCardRequest request) {
        CardResponse response = cardService.createCard(userId, request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponseDto.success(HttpStatus.CREATED, response));
    }

    @PutMapping("/{cardId}")
    public ResponseEntity<ApiResponseDto<CardResponse>> updateCard(
            @PathVariable Long cardId,
            @RequestBody UpdateCardRequest request) {
        CardResponse response = cardService.updateCard(cardId, request);
        return ResponseEntity.ok(ApiResponseDto.success(HttpStatus.OK, response));
    }

    @DeleteMapping("/{cardId}")
    public ResponseEntity<ApiResponseDto<Void>> deleteCard(@PathVariable Long cardId) {
        cardService.deleteCard(cardId);
        return ResponseEntity.status(HttpStatus.NO_CONTENT)
                .body(ApiResponseDto.successWithNoContent());
    }

    @GetMapping("/{cardId}/average-price")
    public ResponseEntity<ApiResponseDto<CardAveragePriceResponse>> getAveragePrice(
            @PathVariable Long cardId) {
        CardAveragePriceResponse response = cardService.getAveragePrice(cardId);
        return ResponseEntity.ok(ApiResponseDto.success(HttpStatus.OK, response));
    }
}
