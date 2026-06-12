package com.rocketcrew.pocat.domain.ai.rag;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.rocketcrew.pocat.domain.ai.rag.event.CardEmbeddingEvent;
import com.rocketcrew.pocat.domain.ai.rag.event.TradePostEmbeddingEvent;
import com.rocketcrew.pocat.domain.ai.rag.event.EmbeddingEventListener;
import com.rocketcrew.pocat.domain.ai.rag.service.EmbeddingService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.BDDMockito.willDoNothing;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.verify;

/**
 * EmbeddingEventListener 단위 테스트.
 * 카드/거래글 생성 경로는 fire-and-forget이므로, 임베딩 실패 시
 * 예외를 삼키고 [EMBEDDING_FAIL] 로그만 남겨야 한다.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("EmbeddingEventListener")
class EmbeddingEventListenerTest {

    @InjectMocks
    private EmbeddingEventListener embeddingEventListener;

    @Mock
    private EmbeddingService embeddingService;

    ListAppender<ILoggingEvent> listAppender;
    Logger logger;

    @BeforeEach
    void setUp() {
        logger = (Logger) LoggerFactory.getLogger(EmbeddingEventListener.class);
        listAppender = new ListAppender<>();
        listAppender.start();
        logger.addAppender(listAppender);
    }

    @AfterEach
    void tearDown() {
        logger.detachAppender(listAppender);
    }

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

    @Test
    @DisplayName("카드 임베딩 실패 시 예외를 삼키고 [EMBEDDING_FAIL] eventType=CARD 로그를 출력한다")
    void onCardEmbedding_swallowsAndLogsOnFailure() {
        // given
        CardEmbeddingEvent event = new CardEmbeddingEvent(7L, "text");
        willThrow(new RuntimeException("vector store error"))
                .given(embeddingService).embedCard(7L, "text");

        // when & then
        assertThatCode(() -> embeddingEventListener.onCardEmbedding(event)).doesNotThrowAnyException();

        assertThat(listAppender.list)
                .anyMatch(e -> {
                    String msg = e.getFormattedMessage();
                    return msg.contains("[EMBEDDING_FAIL]")
                            && msg.contains("eventType=CARD")
                            && msg.contains("targetId=7")
                            && msg.contains("exceptionType=RuntimeException")
                            && msg.contains("reason=vector store error");
                });
    }

    @Test
    @DisplayName("거래글 임베딩 실패 시 예외를 삼키고 [EMBEDDING_FAIL] eventType=TRADE_POST 로그를 출력한다")
    void onTradePostEmbedding_swallowsAndLogsOnFailure() {
        // given
        TradePostEmbeddingEvent event = new TradePostEmbeddingEvent(8L, "content");
        willThrow(new RuntimeException("vector store error"))
                .given(embeddingService).embedTradePost(8L, "content");

        // when & then
        assertThatCode(() -> embeddingEventListener.onTradePostEmbedding(event)).doesNotThrowAnyException();

        assertThat(listAppender.list)
                .anyMatch(e -> {
                    String msg = e.getFormattedMessage();
                    return msg.contains("[EMBEDDING_FAIL]")
                            && msg.contains("eventType=TRADE_POST")
                            && msg.contains("targetId=8")
                            && msg.contains("exceptionType=RuntimeException")
                            && msg.contains("reason=vector store error");
                });
    }
}
