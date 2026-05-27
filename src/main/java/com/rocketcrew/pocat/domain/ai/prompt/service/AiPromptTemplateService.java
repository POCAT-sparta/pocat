package com.rocketcrew.pocat.domain.ai.prompt.service;

import com.rocketcrew.pocat.domain.ai.prompt.entity.AiPromptTemplate;
import com.rocketcrew.pocat.domain.ai.prompt.repository.AiPromptTemplateRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AiPromptTemplateService {

    private final AiPromptTemplateRepository promptTemplateRepository;

    /**
     * 카드 등급별 프롬프트 템플릿 조회.
     * 등급별 프롬프트가 없으면 DEFAULT 프롬프트 반환.
     *
     * @param cardGrade 카드 등급 (PSA_10, PSA_9, BGS_10)
     * @return 프롬프트 텍스트
     */
    public String getPrompt(String cardGrade) {
        String normalized = (cardGrade == null || cardGrade.isBlank()) ? "DEFAULT" : cardGrade.trim().toUpperCase(java.util.Locale.ROOT);
        return promptTemplateRepository
                .findByCardGradeAndIsActiveTrue(normalized)
                .map(AiPromptTemplate::getPromptText)
                .orElseGet(() -> promptTemplateRepository
                        .findByCardGradeAndIsActiveTrue("DEFAULT")
                        .map(AiPromptTemplate::getPromptText)
                        .orElseThrow(() -> new IllegalStateException("DEFAULT 프롬프트를 찾을 수 없습니다")));
    }
}
