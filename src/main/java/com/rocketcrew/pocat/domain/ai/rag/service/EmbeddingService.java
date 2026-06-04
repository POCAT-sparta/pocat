package com.rocketcrew.pocat.domain.ai.rag.service;

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

    private final EmbeddingModel embeddingModel;
    private final VectorStore vectorStore;

    /**
     * 카드 정보를 벡터화하여 VectorStore에 저장.
     *
     * @param cardId 카드 ID
     * @param cardText 카드 설명 텍스트
     */
    public void embedCard(Long cardId, String cardText) {
        try {
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
        } catch (Exception e) {
            log.error("[EMBEDDING_FAIL] eventType=CARD targetId={} exceptionType={} reason={}",
                    cardId, e.getClass().getSimpleName(), e.getMessage(), e);
            // 벡터화 실패는 비즈니스 로직에 영향 없음 (로그만 기록)
        }
    }

    /**
     * 거래글을 벡터화하여 VectorStore에 저장.
     *
     * @param postId 거래글 ID
     * @param content 거래글 내용
     */
    public void embedTradePost(Long postId, String content) {
        try {
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
        } catch (Exception e) {
            log.error("[EMBEDDING_FAIL] eventType=TRADE_POST targetId={} exceptionType={} reason={}",
                    postId, e.getClass().getSimpleName(), e.getMessage(), e);
            // 벡터화 실패는 비즈니스 로직에 영향 없음 (로그만 기록)
        }
    }
}
