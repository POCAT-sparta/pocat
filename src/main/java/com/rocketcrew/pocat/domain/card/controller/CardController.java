package com.rocketcrew.pocat.domain.card.controller;

import com.rocketcrew.pocat.domain.card.dto.request.CardSearchCondition;
import com.rocketcrew.pocat.domain.card.dto.request.CreateCardRequest;
import com.rocketcrew.pocat.domain.card.dto.response.ActiveAuctionSummary;
import com.rocketcrew.pocat.domain.card.dto.response.CardResponse;
import com.rocketcrew.pocat.domain.card.entity.enums.CardCategory;
import com.rocketcrew.pocat.domain.card.entity.enums.CardGrade;
import com.rocketcrew.pocat.domain.card.entity.enums.CardStatus;
import com.rocketcrew.pocat.domain.card.service.CardCommandService;
import com.rocketcrew.pocat.domain.card.service.CardQueryService;
import com.rocketcrew.pocat.domain.order.dto.response.CardAveragePriceResponse;
import com.rocketcrew.pocat.global.dto.ApiResponseDto;
import com.rocketcrew.pocat.global.dto.PageResponseDto;
import com.rocketcrew.pocat.global.exception.common.ErrorCode;
import com.rocketcrew.pocat.global.exception.common.ServiceException;
import com.rocketcrew.pocat.global.ratelimit.RateLimitProperties;
import com.rocketcrew.pocat.global.ratelimit.RedisRateLimiter;
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
@RequestMapping("/api")
public class CardController {

    private final CardQueryService cardQueryService;
    private final CardCommandService cardCommandService;
    private final RedisRateLimiter redisRateLimiter;
    private final RateLimitProperties rateLimitProperties;

    @GetMapping("/v1/cards")
    public ResponseEntity<ApiResponseDto<PageResponseDto<CardResponse>>> getCards(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String setName,
            @RequestParam(required = false) String series,
            @RequestParam(required = false) String rarity,
            @RequestParam(required = false) CardGrade grade,
            @RequestParam(required = false) CardCategory category,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        CardSearchCondition condition = new CardSearchCondition(keyword, series, setName, rarity, grade, category, CardStatus.ACTIVE);
        Page<CardResponse> page = cardQueryService.getCards(condition, pageable);
        List<CardResponse> content = page.getContent();
        PageResponseDto<CardResponse> pageResponse = PageResponseDto.of(page, content);
        return ResponseEntity.ok(ApiResponseDto.success(HttpStatus.OK, pageResponse));
    }

    @GetMapping("/v1/cards/{cardId}")
    public ResponseEntity<ApiResponseDto<CardResponse>> getCard(@PathVariable Long cardId) {
        CardResponse response = cardQueryService.getCard(cardId);
        return ResponseEntity.ok(ApiResponseDto.success(HttpStatus.OK, response));
    }

    @GetMapping("/v1/cards/{cardId}/auctions")
    public ResponseEntity<ApiResponseDto<PageResponseDto<ActiveAuctionSummary>>> getCardAuctions(
            @PathVariable Long cardId,
            @PageableDefault(size = 10, sort = "startedAt", direction = Sort.Direction.DESC) Pageable pageable) {
        Page<ActiveAuctionSummary> page = cardQueryService.getCardAuctions(cardId, pageable);
        return ResponseEntity.ok(ApiResponseDto.success(HttpStatus.OK, PageResponseDto.of(page, page.getContent())));
    }

    @PostMapping("/v1/cards")
    public ResponseEntity<ApiResponseDto<CardResponse>> createCard(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @Valid @RequestBody CreateCardRequest request) {
        if (!redisRateLimiter.isAllowed("rate:user:card:" + userDetails.getUserId(),
                rateLimitProperties.getCardLimit(),
                rateLimitProperties.getCardWindowSeconds())) {
            throw new ServiceException(ErrorCode.RATE_LIMIT_EXCEEDED);
        }
        CardResponse response = cardCommandService.createCard(userDetails.getUserId(), request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponseDto.success(HttpStatus.CREATED, response));
    }
    @GetMapping("/v1/cards/{cardId}/average-price")
    public ResponseEntity<ApiResponseDto<CardAveragePriceResponse>> getAveragePrice(
            @PathVariable Long cardId) {
        CardAveragePriceResponse response = cardQueryService.getAveragePrice(cardId);
        return ResponseEntity.ok(ApiResponseDto.success(HttpStatus.OK, response));
    }

    @GetMapping("/v1/cards/my-requests")
    public ResponseEntity<ApiResponseDto<PageResponseDto<CardResponse>>> getMyRequests(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestParam(required = false) CardStatus status,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        Page<CardResponse> page = cardQueryService.getMyRequests(userDetails.getUserId(), status, pageable);
        PageResponseDto<CardResponse> pageResponse = PageResponseDto.of(page, page.getContent());
        return ResponseEntity.ok(ApiResponseDto.success(HttpStatus.OK, pageResponse));
    }
}
