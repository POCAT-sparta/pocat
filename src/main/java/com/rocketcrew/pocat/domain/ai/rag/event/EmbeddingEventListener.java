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
        embeddingService.embedCard(event.cardId(), event.cardText());
    }

    @Async  // intentionally no executor — embedding doesn't require SecurityContext
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onTradePostEmbedding(TradePostEmbeddingEvent event) {
        log.debug("Embedding trade post after commit: postId={}", event.postId());
        embeddingService.embedTradePost(event.postId(), event.content());
    }
}
