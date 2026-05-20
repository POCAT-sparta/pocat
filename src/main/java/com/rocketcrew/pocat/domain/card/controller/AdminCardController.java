package com.rocketcrew.pocat.domain.card.controller;

import com.rocketcrew.pocat.domain.card.dto.request.RejectCardRequest;
import com.rocketcrew.pocat.domain.card.dto.request.UpdateCardRequest;
import com.rocketcrew.pocat.domain.card.dto.response.CardResponse;
import com.rocketcrew.pocat.domain.card.entity.enums.CardStatus;
import com.rocketcrew.pocat.domain.card.service.CardCommandService;
import com.rocketcrew.pocat.domain.card.service.CardQueryService;
import com.rocketcrew.pocat.global.dto.ApiResponseDto;
import com.rocketcrew.pocat.global.dto.PageResponseDto;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/admin/cards")
public class AdminCardController {

    private final CardQueryService cardQueryService;
    private final CardCommandService cardCommandService;

    @GetMapping("/requests")
    public ResponseEntity<ApiResponseDto<PageResponseDto<CardResponse>>> getRequests(
            @RequestParam(required = false) CardStatus status,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        Page<CardResponse> page = cardQueryService.getRequests(status, pageable);
        PageResponseDto<CardResponse> pageResponse = PageResponseDto.of(page, page.getContent());
        return ResponseEntity.ok(ApiResponseDto.success(HttpStatus.OK, pageResponse));
    }

    @PatchMapping("/{cardId}/approve")
    public ResponseEntity<ApiResponseDto<CardResponse>> approveCard(@PathVariable Long cardId) {
        CardResponse response = cardCommandService.approveCard(cardId);
        return ResponseEntity.ok(ApiResponseDto.success(HttpStatus.OK, response));
    }

    @PatchMapping("/{cardId}/reject")
    public ResponseEntity<ApiResponseDto<CardResponse>> rejectCard(
            @PathVariable Long cardId,
            @Valid @RequestBody RejectCardRequest request) {
        CardResponse response = cardCommandService.rejectCard(cardId, request.rejectReason());
        return ResponseEntity.ok(ApiResponseDto.success(HttpStatus.OK, response));
    }

    @PatchMapping("/{cardId}")
    public ResponseEntity<ApiResponseDto<CardResponse>> updateCard(
            @PathVariable Long cardId,
            @Valid @RequestBody UpdateCardRequest request) {
        CardResponse response = cardCommandService.updateCard(cardId, request);
        return ResponseEntity.ok(ApiResponseDto.success(HttpStatus.OK, response));
    }

    @DeleteMapping("/{cardId}")
    public ResponseEntity<Void> deleteCard(@PathVariable Long cardId) {
        cardCommandService.deleteCard(cardId);
        return ResponseEntity.noContent().build();
    }
}
