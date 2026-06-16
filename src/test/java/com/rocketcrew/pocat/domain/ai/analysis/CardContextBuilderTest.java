package com.rocketcrew.pocat.domain.ai.analysis;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rocketcrew.pocat.domain.ai.analysis.config.AnalysisExchangeRateProperties;
import com.rocketcrew.pocat.domain.ai.analysis.service.CardContextBuilder;
import com.rocketcrew.pocat.domain.card.entity.Card;
import com.rocketcrew.pocat.domain.card.entity.enums.CardCategory;
import com.rocketcrew.pocat.domain.card.entity.enums.CardGrade;
import com.rocketcrew.pocat.domain.card.entity.enums.CardSource;
import com.rocketcrew.pocat.domain.card.entity.enums.CardStatus;
import com.rocketcrew.pocat.support.TestFixtures;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

@DisplayName("CardContextBuilder")
class CardContextBuilderTest {

    private CardContextBuilder cardContextBuilder;
    private RestTemplate tcgdexRestTemplate;

    @BeforeEach
    void setUp() {
        AnalysisExchangeRateProperties props = new AnalysisExchangeRateProperties();
        props.setUsdToKrw(1400.0);
        props.setEurToKrw(1500.0);
        cardContextBuilder = new CardContextBuilder(new ObjectMapper(), props);

        // 내부 RestTemplate를 mock으로 교체 (네트워크 호출 차단)
        tcgdexRestTemplate = mock(RestTemplate.class);
        ReflectionTestUtils.setField(cardContextBuilder, "tcgdexRestTemplate", tcgdexRestTemplate);
    }

    private Card card(String tcgdexId) {
        Card card = Card.builder()
                .userId(1L)
                .tcgdexId(tcgdexId)
                .name("뮤츠")
                .series(TestFixtures.aSeries())
                .pokemonSet(TestFixtures.aPokemonSet())
                .cardNumber("001")
                .rarity("SSR")
                .category(CardCategory.POKEMON)
                .grade(CardGrade.PSA_10)
                .source(CardSource.TCGDEX)
                .status(CardStatus.ACTIVE)
                .build();
        ReflectionTestUtils.setField(card, "id", 1L);
        return card;
    }

    @Test
    @DisplayName("tcgdexId가 없으면 내부 정보만 반환 (TCGdex/환율 블록 없음)")
    void noTcgdexId_internalOnly() {
        String context = cardContextBuilder.build(card(null));

        assertThat(context).contains("카드 이름: 뮤츠");
        assertThat(context).doesNotContain("[TCGdex 실측 데이터]");
        assertThat(context).doesNotContain("[환율 정보]");
    }

    @Test
    @DisplayName("TCGdex 조회 실패 시 fail-open: 내부 정보만 반환")
    void tcgdexFetchFails_failOpen() {
        given(tcgdexRestTemplate.getForObject(anyString(), eq(String.class)))
                .willThrow(new RuntimeException("timeout"));

        String context = cardContextBuilder.build(card("swsh3-136"));

        assertThat(context).contains("카드 이름: 뮤츠");
        assertThat(context).doesNotContain("[TCGdex 실측 데이터]");
        assertThat(context).doesNotContain("[환율 정보]");
    }

    @Test
    @DisplayName("TCGdex 데이터가 있으면 시세 + 환율 블록이 컨텍스트에 주입된다")
    void tcgdexData_injectsPricingAndExchangeRate() {
        String json = """
                {
                  "name": "Furret",
                  "rarity": "Uncommon",
                  "hp": 110,
                  "types": ["Colorless"],
                  "stage": "Stage1",
                  "set": { "name": "Darkness Ablaze", "cardCount": { "total": 201 } },
                  "regulationMark": "D",
                  "legal": { "standard": false, "expanded": true },
                  "pricing": {
                    "cardmarket": { "unit": "EUR", "avg": 0.09, "low": 0.02, "trend": 0.13, "avg30": 0.09 },
                    "tcgplayer": {
                      "unit": "USD",
                      "normal": { "lowPrice": 0.04, "highPrice": 25.18, "marketPrice": 0.08 }
                    }
                  }
                }
                """;
        given(tcgdexRestTemplate.getForObject(anyString(), eq(String.class))).willReturn(json);

        String context = cardContextBuilder.build(card("swsh3-136"));

        assertThat(context).contains("[TCGdex 실측 데이터]");
        assertThat(context).contains("공식명: Furret");
        assertThat(context).contains("시세(cardmarket,EUR)");
        assertThat(context).contains("시세(tcgplayer-일반,USD)");
        assertThat(context).contains("세트: Darkness Ablaze (201장)");
        assertThat(context).contains("[환율 정보]");
        assertThat(context).contains("1 USD = 1400원");
        assertThat(context).contains("1 EUR = 1500원");
    }
}
