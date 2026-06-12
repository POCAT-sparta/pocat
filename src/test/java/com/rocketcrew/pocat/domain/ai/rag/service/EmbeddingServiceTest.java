package com.rocketcrew.pocat.domain.ai.rag.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.VectorStore;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class EmbeddingServiceTest {

    @InjectMocks
    EmbeddingService embeddingService;

    @Mock
    EmbeddingModel embeddingModel;

    @Mock
    VectorStore vectorStore;

    @Test
    @DisplayName("카드 임베딩 성공 시 VectorStore에 문서를 추가한다")
    void embedCard_addsDocumentToVectorStore() {
        // when
        embeddingService.embedCard(1L, "test card text");

        // then
        verify(vectorStore).add(anyList());
    }

    @Test
    @DisplayName("거래글 임베딩 성공 시 VectorStore에 문서를 추가한다")
    void embedTradePost_addsDocumentToVectorStore() {
        // when
        embeddingService.embedTradePost(1L, "test post content");

        // then
        verify(vectorStore).add(anyList());
    }

    @Test
    @DisplayName("카드 임베딩 실패 시 예외를 상위로 전파한다 (벌크 재색인이 실패를 감지할 수 있도록)")
    void embedCard_propagatesException() {
        // given
        doThrow(new RuntimeException("vector store error")).when(vectorStore).add(anyList());

        // when & then
        assertThatThrownBy(() -> embeddingService.embedCard(1L, "text"))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("vector store error");
    }

    @Test
    @DisplayName("거래글 임베딩 실패 시 예외를 상위로 전파한다")
    void embedTradePost_propagatesException() {
        // given
        doThrow(new RuntimeException("vector store error")).when(vectorStore).add(anyList());

        // when & then
        assertThatThrownBy(() -> embeddingService.embedTradePost(1L, "content"))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("vector store error");
    }
}
