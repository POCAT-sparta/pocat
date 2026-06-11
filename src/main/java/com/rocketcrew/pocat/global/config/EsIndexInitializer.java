package com.rocketcrew.pocat.global.config;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import com.rocketcrew.pocat.domain.card.service.CardEsAliasReindexService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * 앱 시작 시 cards ES 인덱스/alias 존재 여부를 확인하고 없으면 초기 생성한다.
 *
 * <p>케이스별 동작:
 * <ul>
 *   <li>alias "cards" 존재 → 정상 운영 중, 스킵</li>
 *   <li>직접 인덱스 "cards" 존재 → 레거시 상태, 스킵 (setupAlias API로 수동 마이그레이션)</li>
 *   <li>둘 다 없음 → 최초 설치: cards_v1 생성 + alias 연결</li>
 * </ul>
 *
 * <p>CardDocument에 createIndex=false 설정과 함께 사용한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class EsIndexInitializer {

    private final ElasticsearchClient esClient;
    private final CardEsAliasReindexService cardEsAliasReindexService;

    @EventListener(ApplicationReadyEvent.class)
    public void ensureCardsIndexExists() {
        try {
            boolean aliasExists  = esClient.indices().existsAlias(r -> r.name("cards")).value();
            boolean indexExists  = esClient.indices().exists(r -> r.index("cards")).value();

            if (aliasExists) {
                log.info("[ES_INIT] alias 'cards' 확인됨 — 스킵");
                return;
            }
            if (indexExists) {
                log.info("[ES_INIT] 직접 인덱스 'cards' 확인됨 (레거시) — " +
                        "POST /api/v1/admin/es-alias-setup 으로 alias 마이그레이션을 진행하세요.");
                return;
            }

            // 최초 설치: cards_v1 + alias 생성
            log.info("[ES_INIT] 'cards' 인덱스/alias 없음 — cards_v1 + alias 초기 생성");
            cardEsAliasReindexService.setupAlias();
            log.info("[ES_INIT] 초기화 완료");

        } catch (Exception e) {
            // ES 연결 실패 등 인프라 문제 시 앱 시작은 막지 않음
            log.warn("[ES_INIT] 초기화 확인 실패 (앱 시작은 계속됩니다): {}", e.getMessage());
        }
    }
}
