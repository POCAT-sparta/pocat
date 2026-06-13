package com.rocketcrew.pocat.domain.ai.rag.service;

import com.rocketcrew.pocat.domain.ai.rag.exception.EmbeddingRateLimitedException;
import com.rocketcrew.pocat.global.ratelimit.RedisRateLimiter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

/**
 * 임베딩 및 벡터 스토어 관리 서비스.
 * 카드 정보와 거래글을 벡터화하여 Redis VectorStore에 저장.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class EmbeddingService {

    private static final String AI_EMBEDDING_RATE_LIMIT_KEY = "ratelimit:ai-embedding";
    private static final int AI_EMBEDDING_RATE_LIMIT = 80;
    private static final long AI_EMBEDDING_RATE_LIMIT_WINDOW_SECONDS = 60;

    private final EmbeddingModel embeddingModel;
    private final VectorStore vectorStore;
    private final RedisRateLimiter redisRateLimiter;

    /**
     * 카드 정보를 벡터화하여 VectorStore에 저장.
     *
     * @param cardId 카드 ID
     * @param cardText 카드 설명 텍스트
     */
    public void embedCard(Long cardId, String cardText) {
        log.info("Embedding card: cardId={}", cardId);

        Document document = new Document(
                cardText,
                Map.of(
                        "type", "card",
                        "cardId", String.valueOf(cardId)
                )
        );

        vectorStore.add(java.util.List.of(document));
        log.debug("Card embedded successfully: cardId={}", cardId);
    }

    /**
     * rate limit을 적용하여 카드 정보를 벡터화하여 VectorStore에 저장.
     *
     * <p>분당 호출 한도({@value #AI_EMBEDDING_RATE_LIMIT}회)를 초과하면
     * {@link EmbeddingRateLimitedException}을 던지고 embedCard()를 호출하지 않는다.
     *
     * @param cardId 카드 ID
     * @param cardText 카드 설명 텍스트
     * @throws EmbeddingRateLimitedException rate limit 도달 시
     */
    public void embedCardRateLimited(Long cardId, String cardText) {
        boolean allowed = redisRateLimiter.isAllowed(
                AI_EMBEDDING_RATE_LIMIT_KEY,
                AI_EMBEDDING_RATE_LIMIT,
                AI_EMBEDDING_RATE_LIMIT_WINDOW_SECONDS
        );

        if (!allowed) {
            log.warn("[AI_EMBEDDING] rate limit 도달, 임베딩 건너뜀: cardId={}", cardId);
            throw new EmbeddingRateLimitedException("AI 임베딩 rate limit에 도달했습니다.");
        }

        embedCard(cardId, cardText);
    }

    /**
     * 거래글을 벡터화하여 VectorStore에 저장.
     *
     * @param postId 거래글 ID
     * @param content 거래글 내용
     */
    public void embedTradePost(Long postId, String content) {
        log.info("Embedding trade post: postId={}", postId);

        Document document = new Document(
                content,
                Map.of(
                        "type", "tradepost",
                        "postId", String.valueOf(postId)
                )
        );

        vectorStore.add(java.util.List.of(document));
        log.debug("Trade post embedded successfully: postId={}", postId);
    }
}
