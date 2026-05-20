package com.rocketcrew.pocat.domain.card.controller;

import com.rocketcrew.pocat.domain.card.dto.request.CardSearchCondition;
import com.rocketcrew.pocat.domain.card.dto.request.CreateCardRequest;
import com.rocketcrew.pocat.domain.card.dto.response.CardResponse;
import com.rocketcrew.pocat.domain.card.entity.enums.CardCategory;
import com.rocketcrew.pocat.domain.card.entity.enums.CardGrade;
import com.rocketcrew.pocat.domain.card.entity.enums.CardStatus;
import com.rocketcrew.pocat.domain.card.service.CardCommandService;
import com.rocketcrew.pocat.domain.card.service.CardQueryService;
import com.rocketcrew.pocat.domain.order.dto.response.CardAveragePriceResponse;
import com.rocketcrew.pocat.global.dto.ApiResponseDto;
import com.rocketcrew.pocat.global.dto.PageResponseDto;
import com.rocketcrew.pocat.global.security.CustomUserDetails;
import jakarta.validation.Valid;
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
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String setName,
            @RequestParam(required = false) CardGrade grade,
            @RequestParam(required = false) CardCategory category,
            @PageableDefault(size = 10, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        CardSearchCondition condition = new CardSearchCondition(keyword, setName, grade, category, CardStatus.ACTIVE);
        Page<CardResponse> page = cardQueryService.getCards(condition, pageable);
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
            @Valid @RequestBody CreateCardRequest request) {
        CardResponse response = cardCommandService.createCard(userDetails.getUserId(), request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponseDto.success(HttpStatus.CREATED, response));
    }
    @GetMapping("/{cardId}/average-price")
    public ResponseEntity<ApiResponseDto<CardAveragePriceResponse>> getAveragePrice(
            @PathVariable Long cardId) {
        CardAveragePriceResponse response = cardQueryService.getAveragePrice(cardId);
        return ResponseEntity.ok(ApiResponseDto.success(HttpStatus.OK, response));
    }

    @GetMapping("/my-requests")
    public ResponseEntity<ApiResponseDto<PageResponseDto<CardResponse>>> getMyRequests(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestParam(required = false) CardStatus status,
            @PageableDefault(size = 10, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        Page<CardResponse> page = cardQueryService.getMyRequests(userDetails.getUserId(), status, pageable);
        PageResponseDto<CardResponse> pageResponse = PageResponseDto.of(page, page.getContent());
        return ResponseEntity.ok(ApiResponseDto.success(HttpStatus.OK, pageResponse));
    }
}
