package com.rocketcrew.pocat.domain.ai.analysis.config;

import jakarta.validation.constraints.Positive;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

/**
 * AI 카드 분석 시 TCGdex 외화 시세(EUR/USD)를 원화로 환산하기 위한 환율.
 *
 * <p>현재는 설정 기반 고정 환율이다. 결정적(deterministic)이라 LLM이 임의 환율을 쓰는 것을 막는다.
 * 추후 실시간 환율 API로 교체하려면 이 프로퍼티 대신 환율 Provider를 주입하도록 바꾸면 된다.
 */
@Getter
@Setter
@Validated
@Component
@ConfigurationProperties(prefix = "analysis.exchange-rate")
public class AnalysisExchangeRateProperties {

    /** 1 USD = ? 원 (tcgplayer 시세 환산용). */
    @Positive
    private double usdToKrw = 1400.0;

    /** 1 EUR = ? 원 (cardmarket 시세 환산용). */
    @Positive
    private double eurToKrw = 1500.0;
}
