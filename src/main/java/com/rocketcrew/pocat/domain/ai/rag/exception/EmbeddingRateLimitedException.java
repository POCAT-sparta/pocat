package com.rocketcrew.pocat.domain.ai.rag.exception;

/**
 * 카드 임베딩 rate limit(분당 호출 제한) 도달 시 발생하는 예외.
 */
public class EmbeddingRateLimitedException extends RuntimeException {

    public EmbeddingRateLimitedException(String message) {
        super(message);
    }
}
