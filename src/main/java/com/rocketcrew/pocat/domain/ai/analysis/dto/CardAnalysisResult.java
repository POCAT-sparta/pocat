package com.rocketcrew.pocat.domain.ai.analysis.dto;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 카드 AI 분석 결과 DTO.
 * Spring AI의 BeanOutputConverter와 호환 가능한 record 형식.
 */
public record CardAnalysisResult(
        String priceTrend,              // RISING/STABLE/FALLING
        Long fairValueEstimate,         // 공정 가치 추정
        String demandLevel,             // HIGH/MEDIUM/LOW
        String summary,                 // 분석 요약
        List<String> highlights,        // 주요 특징
        List<String> riskFactors,       // 위험 요소
        List<String> keywords,          // 관련 키워드
        String analysisModel,           // 사용된 LLM 모델명
        Integer promptTokens,           // 프롬프트 토큰 수
        Integer completionTokens,       // 완료 토큰 수
        LocalDateTime analyzedAt        // 분석 시각
) {
}
