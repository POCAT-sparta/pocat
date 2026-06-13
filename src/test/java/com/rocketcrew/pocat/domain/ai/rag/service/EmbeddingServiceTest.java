package com.rocketcrew.pocat.domain.ai.rag.service;

import com.rocketcrew.pocat.domain.ai.rag.exception.EmbeddingRateLimitedException;
import com.rocketcrew.pocat.global.ratelimit.RateLimitProperties;
import com.rocketcrew.pocat.global.ratelimit.RedisRateLimiter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.VectorStore;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class EmbeddingServiceTest {

    @InjectMocks
    EmbeddingService embeddingService;

    @Mock
    EmbeddingModel embeddingModel;

    @Mock
    VectorStore vectorStore;

    @Mock
    RedisRateLimiter redisRateLimiter;

    @Spy
    RateLimitProperties rateLimitProperties = new RateLimitProperties();

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

    // ---------------------------------------------------------------
    // embedCardRateLimited
    // ---------------------------------------------------------------
    @Nested
    @DisplayName("embedCardRateLimited")
    class EmbedCardRateLimited {

        @Test
        @DisplayName("rate limit 허용 시 embedCard를 호출하여 VectorStore에 문서를 추가한다")
        void allowed_callsEmbedCard() {
            // given
            given(redisRateLimiter.isAllowed("ratelimit:ai-embedding", 80, 60)).willReturn(true);

            // when
            embeddingService.embedCardRateLimited(1L, "test card text");

            // then
            verify(vectorStore).add(anyList());
        }

        @Test
        @DisplayName("rate limit 도달 시 EmbeddingRateLimitedException을 던지고 embedCard를 호출하지 않는다")
        void blocked_throwsEmbeddingRateLimitedException() {
            // given
            given(redisRateLimiter.isAllowed("ratelimit:ai-embedding", 80, 60)).willReturn(false);

            // when & then
            assertThatThrownBy(() -> embeddingService.embedCardRateLimited(1L, "test card text"))
                    .isInstanceOf(EmbeddingRateLimitedException.class);

            verify(vectorStore, never()).add(anyList());
        }

        @Test
        @DisplayName("rate limit 체크 시 정해진 key/limit/window로 RedisRateLimiter를 호출한다")
        void allowed_usesExpectedRateLimitKeyAndParams() {
            // given
            given(redisRateLimiter.isAllowed(anyString(), anyInt(), anyLong())).willReturn(true);

            // when
            embeddingService.embedCardRateLimited(2L, "another card text");

            // then
            verify(redisRateLimiter).isAllowed("ratelimit:ai-embedding", 80, 60);
        }
    }
}
