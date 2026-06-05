package com.rocketcrew.pocat.domain.card.service;

import com.rocketcrew.pocat.domain.card.entity.Card;
import com.rocketcrew.pocat.domain.card.repository.CardRepository;
import com.rocketcrew.pocat.global.infra.s3.S3Uploader;
import static com.rocketcrew.pocat.global.infra.s3.S3Uploader.CARD_IMAGE_CONTENT_TYPE;
import static com.rocketcrew.pocat.global.infra.s3.S3Uploader.cardImageKey;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.List;

/**
 * TCGDex CDN URL → S3 이미지 일괄 마이그레이션 서비스.
 *
 * <p>실행 흐름:
 * <pre>
 * 1. DB에서 imageUrl이 assets.tcgdex.net으로 시작하는 카드를 배치 단위로 커서 기반 조회
 * 2. TCGDex CDN에서 이미지 바이트 다운로드
 * 3. S3에 "cards/{tcgdexId}/high.webp" 키로 업로드
 * 4. Card.imageUrl을 S3 URL로 업데이트
 * </pre>
 *
 * <p>Admin API(POST /api/v1/admin/cards/migrate-images)를 통해 수동 실행한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CardImageMigrationService {

    private static final int BATCH_SIZE = 50;
    private static final long DELAY_MS  = 100; // TCGDex rate limit 방지

    private final CardRepository     cardRepository;
    private final CardCommandService cardCommandService;
    private final S3Uploader         s3Uploader;

    @Async("syncExecutor")
    public void migrateAll() {
        long total   = cardRepository.countTcgdexImageCards();
        int  success = 0;
        int  failed  = 0;
        long lastId  = 0L;

        log.info("[ImageMigration] 시작 — 대상 카드: {}개", total);

        RestTemplate restTemplate = createRestTemplate();
        List<Card> batch;

        do {
            batch = cardRepository.findTcgdexImageCardsAfterId(lastId, PageRequest.of(0, BATCH_SIZE));

            for (Card card : batch) {
                lastId = card.getId(); // 성공/실패 무관하게 커서 전진
                try {
                    if (card.getTcgdexId() == null || card.getTcgdexId().isBlank()) {
                        throw new IllegalStateException("tcgdexId 없음: cardId=" + card.getId());
                    }
                    String s3Url = downloadAndUpload(restTemplate, card);
                    card.updateImageUrl(s3Url);
                    cardRepository.save(card);
                    success++;
                    try {
                        cardCommandService.indexCard(card);
                    } catch (Exception esEx) {
                        log.warn("[ImageMigration] ES 색인 실패 ({}): {}", card.getTcgdexId(), esEx.getMessage());
                    }

                    log.debug("[ImageMigration] 완료 ({}/{}): {}", success + failed, total, card.getTcgdexId());
                    Thread.sleep(DELAY_MS);

                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    log.error("[ImageMigration] 인터럽트 발생 — 중단");
                    return;
                } catch (Exception e) {
                    failed++;
                    log.warn("[ImageMigration] 실패 ({}): {}", card.getTcgdexId(), e.getMessage());
                }
            }

        } while (!batch.isEmpty());

        log.info("[ImageMigration] 완료 — 성공: {}개, 실패: {}개", success, failed);
    }

    private String downloadAndUpload(RestTemplate restTemplate, Card card) {
        byte[] imageBytes = restTemplate.getForObject(card.getImageUrl(), byte[].class);
        if (imageBytes == null || imageBytes.length == 0) {
            throw new IllegalStateException("이미지 다운로드 실패: " + card.getImageUrl());
        }

        return s3Uploader.upload(cardImageKey(card.getTcgdexId()), imageBytes, CARD_IMAGE_CONTENT_TYPE);
    }

    private RestTemplate createRestTemplate() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(5_000);
        factory.setReadTimeout(15_000);
        return new RestTemplate(factory);
    }
}
