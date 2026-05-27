package com.rocketcrew.pocat.domain.ai.analysis.entity;

import com.rocketcrew.pocat.global.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "card_ai_analysis", indexes = @Index(name = "idx_card_ai_analysis_card_id", columnList = "card_id"))
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CardAiAnalysis extends BaseEntity {

    @Column(nullable = false)
    private Long cardId;

    @Column(nullable = false, length = 10)
    private String priceTrend;

    @Column(nullable = true)
    private Long fairValueEstimate;

    @Column(nullable = false, length = 10)
    private String demandLevel;

    @Column(nullable = true, columnDefinition = "TEXT")
    private String summary;

    @Column(nullable = true, columnDefinition = "TEXT")
    private String highlights;

    @Column(nullable = true, columnDefinition = "TEXT")
    private String riskFactors;

    @Column(nullable = true, columnDefinition = "TEXT")
    private String keywords;

    @Column(nullable = true, length = 100)
    private String analysisModel;

    @Column(nullable = true)
    private Integer promptTokens;

    @Column(nullable = true)
    private Integer completionTokens;
}
