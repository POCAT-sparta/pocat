package com.rocketcrew.pocat.domain.ai.rag.service;

import com.rocketcrew.pocat.domain.card.entity.Card;
import com.rocketcrew.pocat.domain.card.entity.enums.CardStatus;
import com.rocketcrew.pocat.domain.card.repository.CardRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

/**
 * 어드민용 AI RAG 관리 서비스.
 * 활성 카드 전체를 벡터 스토어에 재색인하는 벌크 작업을 제공한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminAiService {

    private static final int PAGE_SIZE = 100;

    private final CardRepository cardRepository;
    private final EmbeddingService embeddingService;

    private final java.util.concurrent.atomic.AtomicBoolean reindexRunning = new java.util.concurrent.atomic.AtomicBoolean(false);

    /**
     * 활성 카드 전체를 벡터 스토어에 재색인한다.
     *
     * <p>비동기로 실행되며 호출 즉시 반환된다.
     * 기존 card 타입 벡터 삭제는 VectorStore filter-delete 미지원으로 생략하고
     * embedCard() 호출 시 동일 cardId Document가 upsert 된다.
     */
    @Async
    public void reindexAll() {
        if (!reindexRunning.compareAndSet(false, true)) {
            log.warn("[AdminAiService] 재색인이 이미 실행 중입니다. 중복 실행을 무시합니다.");
            return;
        }
        try {
            log.info("[AdminAiService] 전체 카드 재색인 시작");
            log.warn("[AdminAiService] 기존 card 타입 벡터 삭제는 VectorStore filter-delete 미지원으로 생략합니다. embedCard()로 upsert 처리됩니다.");

            int page = 0;
            int totalIndexed = 0;
            int failedCount = 0;

            while (true) {
                Page<Card> cardPage = cardRepository.findWithDetailsByStatus(
                        CardStatus.ACTIVE, PageRequest.of(page, PAGE_SIZE));

                if (cardPage.isEmpty()) {
                    break;
                }

                for (Card card : cardPage.getContent()) {
                    String cardText = String.format(
                            "카드 이름: %s\n등급: %s\n시리즈: %s\n세트: %s\nURL: %s\n레어도: %s",
                            card.getName(),
                            card.getGrade().toString(),
                            card.getSeries() != null ? card.getSeries().getName() : "N/A",
                            card.getPokemonSet() != null ? card.getPokemonSet().getName() : "N/A",
                            card.getImageUrl() != null ? card.getImageUrl() : "N/A",
                            card.getRarity()
                    );
                    boolean indexed = false;
                    for (int attempt = 1; attempt <= 2; attempt++) {
                        try {
                            embeddingService.embedCard(card.getId(), cardText);
                            indexed = true;
                            break;
                        } catch (Exception e) {
                            if (attempt < 2) {
                                log.warn("[AdminAiService] 카드 색인 재시도: cardId={}, attempt={}", card.getId(), attempt);
                            } else {
                                log.error("[AdminAiService] 카드 색인 최종 실패: cardId={}", card.getId(), e);
                                failedCount++;
                            }
                        }
                    }
                    if (indexed) {
                        totalIndexed++;
                    }
                }

                log.info("[AdminAiService] 페이지 {} 처리 완료 ({}건)",
                        page, cardPage.getNumberOfElements());

                if (cardPage.isLast()) {
                    break;
                }
                page++;
            }

            log.info("[AdminAiService] 전체 카드 재색인 완료: 성공={}건, 실패={}건", totalIndexed, failedCount);
        } finally {
            reindexRunning.set(false);
        }
    }
}
