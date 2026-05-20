package com.rocketcrew.pocat.domain.card.controller;

import com.rocketcrew.pocat.domain.card.dto.request.UpdateCardRequest;
import com.rocketcrew.pocat.domain.card.dto.response.CardResponse;
import com.rocketcrew.pocat.domain.card.service.CardCommandService;
import com.rocketcrew.pocat.global.dto.ApiResponseDto;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;


@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/admin/cards")
public class AdminCardController {

    private final CardCommandService cardCommandService;

    @PutMapping("/{cardId}")
    public ResponseEntity<ApiResponseDto<CardResponse>> updateCard(
            @PathVariable Long cardId,
            @RequestBody UpdateCardRequest request) {
        CardResponse response = cardCommandService.updateCard(cardId, request);
        return ResponseEntity.ok(ApiResponseDto.success(HttpStatus.OK, response));
    }

    @DeleteMapping("/{cardId}")
    public ResponseEntity<Void> deleteCard(@PathVariable Long cardId) {
        cardCommandService.deleteCard(cardId);
        return ResponseEntity.noContent().build();
    }
}
