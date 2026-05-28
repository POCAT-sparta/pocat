package com.rocketcrew.pocat.domain.ai.rag.event;

public record TradePostEmbeddingEvent(Long postId, String content) {}
