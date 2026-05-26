package com.rocketcrew.pocat.domain.ai.analysis.controller;

import com.rocketcrew.pocat.domain.ai.analysis.dto.CardAnalysisResult;
import com.rocketcrew.pocat.domain.ai.analysis.service.CardAnalysisService;
import com.rocketcrew.pocat.global.dto.ApiResponseDto;
import com.rocketcrew.pocat.global.security.CustomUserDetails;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/ai/cards")
@RequiredArgsConstructor
@PreAuthorize("isAuthenticated()")
public class CardAnalysisController {

    private final CardAnalysisService cardAnalysisService;

    /**
     * 카드 분석 결과 조회 (캐시 우선).
     *
     * @param cardId 카드 ID
     * @param userDetails 인증된 사용자 정보
     * @return 분석 결과
     */
    @GetMapping("/{cardId}/analysis")
    public ResponseEntity<ApiResponseDto<CardAnalysisResult>> getCardAnalysis(
            @PathVariable Long cardId,
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        CardAnalysisResult result = cardAnalysisService.analyzeCard(cardId);
        return ResponseEntity.ok(ApiResponseDto.success(HttpStatus.OK, result));
    }

    /**
     * 카드 분석 강제 재생성 (캐시 무효화).
     *
     * @param cardId 카드 ID
     * @param userDetails 인증된 사용자 정보
     * @return 새로운 분석 결과
     */
    @PostMapping("/{cardId}/analysis")
    public ResponseEntity<ApiResponseDto<CardAnalysisResult>> reanalyzeCard(
            @PathVariable Long cardId,
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        CardAnalysisResult result = cardAnalysisService.reanalyzeCard(cardId);
        return ResponseEntity.ok(ApiResponseDto.success(HttpStatus.OK, result));
    }
}
