package com.rocketcrew.pocat.domain.ai.rag.service;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.VectorStore;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.doThrow;

@ExtendWith(MockitoExtension.class)
class EmbeddingServiceTest {

    @InjectMocks
    EmbeddingService embeddingService;

    @Mock
    EmbeddingModel embeddingModel;

    @Mock
    VectorStore vectorStore;

    ListAppender<ILoggingEvent> listAppender;
    Logger logger;

    @BeforeEach
    void setUp() {
        logger = (Logger) LoggerFactory.getLogger(EmbeddingService.class);
        listAppender = new ListAppender<>();
        listAppender.start();
        logger.addAppender(listAppender);
    }

    @AfterEach
    void tearDown() {
        logger.detachAppender(listAppender);
    }

    @Test
    @DisplayName("카드 임베딩 실패 시 [EMBEDDING_FAIL] eventType=CARD 로그를 출력한다")
    void embedCard_logsEmbeddingFailWithCardType() {
        // given
        doThrow(new RuntimeException("vector store error")).when(vectorStore).add(anyList());

        // when
        embeddingService.embedCard(1L, "test card text");

        // then
        assertThat(listAppender.list)
                .anyMatch(event -> {
                    String msg = event.getFormattedMessage();
                    return msg.contains("[EMBEDDING_FAIL]")
                            && msg.contains("eventType=CARD")
                            && msg.contains("targetId=1")
                            && msg.contains("exceptionType=RuntimeException")
                            && msg.contains("reason=vector store error");
                });
    }

    @Test
    @DisplayName("거래글 임베딩 실패 시 [EMBEDDING_FAIL] eventType=TRADE_POST 로그를 출력한다")
    void embedTradePost_logsEmbeddingFailWithTradePostType() {
        // given
        doThrow(new RuntimeException("vector store error")).when(vectorStore).add(anyList());

        // when
        embeddingService.embedTradePost(1L, "test post content");

        // then
        assertThat(listAppender.list)
                .anyMatch(event -> {
                    String msg = event.getFormattedMessage();
                    return msg.contains("[EMBEDDING_FAIL]")
                            && msg.contains("eventType=TRADE_POST")
                            && msg.contains("targetId=1")
                            && msg.contains("exceptionType=RuntimeException")
                            && msg.contains("reason=vector store error");
                });
    }

    @Test
    @DisplayName("임베딩 실패 시 예외가 상위로 전파되지 않는다")
    void embedCard_doesNotPropagateException() {
        // given
        doThrow(new RuntimeException("vector store error")).when(vectorStore).add(anyList());

        // when & then
        assertThatCode(() -> embeddingService.embedCard(1L, "text")).doesNotThrowAnyException();
    }
}
