package com.rocketcrew.pocat.domain.ai.rag.event;

import com.rocketcrew.pocat.domain.ai.rag.service.EmbeddingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
@RequiredArgsConstructor
public class EmbeddingEventListener {

    private final EmbeddingService embeddingService;

    @Async  // intentionally no executor — embedding doesn't require SecurityContext
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onCardEmbedding(CardEmbeddingEvent event) {
        log.debug("Embedding card after commit: cardId={}", event.cardId());
        try {
            embeddingService.embedCard(event.cardId(), event.cardText());
        } catch (Exception e) {
            // 생성 트랜잭션은 이미 커밋됨 — 임베딩 실패는 비즈니스 로직에 영향 없음 (로그만 기록)
            log.error("[EMBEDDING_FAIL] 카드 임베딩 실패 eventType=CARD targetId={} exceptionType={} reason={}",
                    event.cardId(), e.getClass().getSimpleName(), e.getMessage(), e);
        }
    }

    @Async  // intentionally no executor — embedding doesn't require SecurityContext
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onTradePostEmbedding(TradePostEmbeddingEvent event) {
        log.debug("Embedding trade post after commit: postId={}", event.postId());
        try {
            embeddingService.embedTradePost(event.postId(), event.content());
        } catch (Exception e) {
            // 생성 트랜잭션은 이미 커밋됨 — 임베딩 실패는 비즈니스 로직에 영향 없음 (로그만 기록)
            log.error("[EMBEDDING_FAIL] 거래글 임베딩 실패 eventType=TRADE_POST targetId={} exceptionType={} reason={}",
                    event.postId(), e.getClass().getSimpleName(), e.getMessage(), e);
        }
    }
}
