package com.rocketcrew.pocat.domain.ai.analysis.dto.response;

import com.rocketcrew.pocat.domain.ai.analysis.dto.CardAnalysisResult;

import java.util.List;

/**
 * 카드 AI 분석 API 응답 DTO.
 * LLM 내부 메타데이터(모델명, 토큰 수, 분석 시각)를 제외한 7개 필드만 노출.
 */
public record CardAnalysisResponse(
        String priceTrend,
        Long fairValueEstimate,
        String demandLevel,
        String summary,
        List<String> highlights,
        List<String> riskFactors,
        List<String> keywords
) {
    public static CardAnalysisResponse from(CardAnalysisResult r) {
        return new CardAnalysisResponse(
                r.priceTrend(),
                r.fairValueEstimate(),
                r.demandLevel(),
                r.summary(),
                r.highlights(),
                r.riskFactors(),
                r.keywords()
        );
    }
}
