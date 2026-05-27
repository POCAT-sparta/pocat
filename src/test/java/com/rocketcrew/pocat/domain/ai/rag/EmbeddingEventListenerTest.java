package com.rocketcrew.pocat.domain.ai.rag;

import com.rocketcrew.pocat.domain.ai.rag.event.CardEmbeddingEvent;
import com.rocketcrew.pocat.domain.ai.rag.event.TradePostEmbeddingEvent;
import com.rocketcrew.pocat.domain.ai.rag.listener.EmbeddingEventListener;
import com.rocketcrew.pocat.domain.ai.rag.service.EmbeddingService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.BDDMockito.willDoNothing;
import static org.mockito.Mockito.verify;

/**
 * EmbeddingEventListener 단위 테스트.
 * 이 클래스들은 #116 인프라 픽스에서 신규 생성되므로
 * 구현 전까지 컴파일 에러로 실패한다 (Red 상태).
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("EmbeddingEventListener")
class EmbeddingEventListenerTest {

    @InjectMocks
    private EmbeddingEventListener embeddingEventListener;

    @Mock
    private EmbeddingService embeddingService;

    @Test
    @DisplayName("CardEmbeddingEvent 수신 시 embeddingService.embedCard()가 호출되어야 한다")
    void onCardEmbedding_callsEmbedCardService() {
        // given
        CardEmbeddingEvent event = new CardEmbeddingEvent(42L, "피카츄 PSA10 카드");
        willDoNothing().given(embeddingService).embedCard(42L, "피카츄 PSA10 카드");

        // when
        embeddingEventListener.onCardEmbedding(event);

        // then
        verify(embeddingService).embedCard(42L, "피카츄 PSA10 카드");
    }

    @Test
    @DisplayName("TradePostEmbeddingEvent 수신 시 embeddingService.embedTradePost()가 호출되어야 한다")
    void onTradePostEmbedding_callsEmbedTradePostService() {
        // given
        TradePostEmbeddingEvent event = new TradePostEmbeddingEvent(99L, "피카츄 PSA10 판매합니다");
        willDoNothing().given(embeddingService).embedTradePost(99L, "피카츄 PSA10 판매합니다");

        // when
        embeddingEventListener.onTradePostEmbedding(event);

        // then
        verify(embeddingService).embedTradePost(99L, "피카츄 PSA10 판매합니다");
    }
}
