package com.rocketcrew.pocat.domain.card.service;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import com.rocketcrew.pocat.global.dto.EsReindexResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

/**
 * ES Index Alias 기반 무중단 재인덱싱 서비스.
 *
 * <p>구조: 앱은 항상 alias "cards"를 바라봄 → 실제 인덱스는 cards_v1, cards_v2, ...
 *
 * <p>settings: es-settings/cards-settings.json
 * <p>mapping : es-settings/cards-mapping.json (CardDocument 변경 시 함께 수정)
 *
 * <p>사용 순서:
 * <ol>
 *   <li>최초 1회: POST /api/v1/admin/es-alias-setup
 *       → cards 직접 인덱스를 cards_v1으로 복사 후 alias 교체</li>
 *   <li>매핑 변경 시: CardDocument + cards-mapping.json 수정 후
 *       POST /api/v1/admin/es-reindex → 무중단 교체</li>
 * </ol>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CardEsAliasReindexService {

    private static final String ALIAS           = "cards";
    private static final String SETTINGS_PATH   = "es-settings/cards-settings.json";
    private static final String MAPPING_PATH    = "es-settings/cards-mapping.json";

    private final ElasticsearchClient esClient;

    // ─── public API ────────────────────────────────────────────────

    /**
     * alias 초기 세팅 (최초 1회).
     * cards 직접 인덱스 → cards_v1 + alias "cards" 교체.
     */
    public EsReindexResponse setupAlias() {
        try {
            boolean aliasExists = esClient.indices().existsAlias(r -> r.name(ALIAS)).value();
            if (aliasExists) {
                log.info("[ES_ALIAS_SETUP] alias '{}' 이미 존재 — 스킵", ALIAS);
                return new EsReindexResponse(ALIAS, ALIAS, 0L, "alias 이미 존재 — 스킵");
            }

            boolean directIndexExists = esClient.indices().exists(r -> r.index(ALIAS)).value();
            if (!directIndexExists) {
                // ES가 완전히 비어 있는 경우 (최초 설치): 빈 cards_v1 + alias 생성
                createNewIndex("cards_v1");
                esClient.indices().putAlias(r -> r.index("cards_v1").name(ALIAS));
                log.info("[ES_ALIAS_SETUP] 신규 생성: cards_v1 + alias '{}'", ALIAS);
                return new EsReindexResponse("(없음)", "cards_v1", 0L, "신규 생성 완료");
            }

            // 기존 cards 직접 인덱스 → cards_v1 마이그레이션
            log.info("[ES_ALIAS_SETUP] 기존 '{}' 직접 인덱스 → cards_v1 마이그레이션 시작", ALIAS);
            createNewIndex("cards_v1");
            long count = callReindex(ALIAS, "cards_v1");

            // 직접 인덱스 삭제 후 alias 연결
            esClient.indices().delete(r -> r.index(ALIAS));
            esClient.indices().putAlias(r -> r.index("cards_v1").name(ALIAS));

            log.info("[ES_ALIAS_SETUP] 완료: cards → cards_v1 + alias 생성 ({}건)", count);
            return new EsReindexResponse("cards", "cards_v1", count, "alias 초기 세팅 완료");

        } catch (IOException e) {
            throw new RuntimeException("ES alias 세팅 중 오류 발생", e);
        }
    }

    /**
     * 무중단 재인덱싱: cards_vN → cards_v(N+1), alias 원자 교체.
     *
     * <p>순서:
     * <ol>
     *   <li>새 인덱스(cards_v(N+1)) 생성</li>
     *   <li>_reindex: 현재 인덱스 전체 복사</li>
     *   <li>delta sync: reindex 시작 이후 updatedAt 기준 변경분 따라잡기</li>
     *   <li>alias 원자 교체 (다운타임 0)</li>
     *   <li>구 인덱스 삭제</li>
     * </ol>
     */
    public EsReindexResponse reindex() {
        try {
            boolean aliasExists = esClient.indices().existsAlias(r -> r.name(ALIAS)).value();
            if (!aliasExists) {
                throw new IllegalStateException(
                        "alias '" + ALIAS + "'가 없습니다. POST /api/v1/admin/es-alias-setup 을 먼저 실행하세요.");
            }

            String currentIndex = getCurrentIndexForAlias();
            String newIndex = nextVersion(currentIndex);
            log.info("[ES_ALIAS_REINDEX] 시작 — {} → {}", currentIndex, newIndex);
            Instant reindexStart = Instant.now();

            // 1. 새 인덱스 생성 (CardDocument 매핑 기준)
            createNewIndex(newIndex);

            // 2. 전체 _reindex
            long reindexed = callReindex(currentIndex, newIndex);
            log.info("[ES_ALIAS_REINDEX] _reindex 완료: {}건", reindexed);

            // 3. delta sync: _reindex 진행 중 변경된 문서 따라잡기
            long delta = deltaSync(currentIndex, newIndex, reindexStart);
            log.info("[ES_ALIAS_REINDEX] delta sync 완료: {}건", delta);

            // 4. alias 원자 교체 (클라이언트 다운타임 없음)
            swapAlias(currentIndex, newIndex);
            log.info("[ES_ALIAS_REINDEX] alias 교체: {} → {}", currentIndex, newIndex);

            // 5. 구 인덱스 삭제
            esClient.indices().delete(r -> r.index(currentIndex));
            log.info("[ES_ALIAS_REINDEX] 구 인덱스 삭제 완료: {}", currentIndex);

            return new EsReindexResponse(currentIndex, newIndex, reindexed + delta, "재인덱싱 완료");

        } catch (IOException e) {
            throw new RuntimeException("ES 재인덱싱 중 오류 발생", e);
        }
    }

    // ─── private helpers ────────────────────────────────────────────

    /** alias가 가리키는 실제 인덱스 이름 반환 */
    private String getCurrentIndexForAlias() throws IOException {
        var resp = esClient.indices().getAlias(r -> r.name(ALIAS));
        return resp.result().keySet().stream()
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "alias '" + ALIAS + "'의 대상 인덱스를 찾을 수 없습니다."));
    }

    /** cards_v1 → cards_v2, cards_v2 → cards_v3 ... */
    private String nextVersion(String currentIndex) {
        if (currentIndex.matches("cards_v\\d+")) {
            int v = Integer.parseInt(currentIndex.replaceAll("[^0-9]", ""));
            return "cards_v" + (v + 1);
        }
        return "cards_v1";
    }

    /**
     * cards-settings.json + cards-mapping.json 기준으로 새 인덱스 생성.
     * CardDocument 매핑 변경 시 cards-mapping.json 도 함께 수정해야 한다.
     */
    private void createNewIndex(String indexName) throws IOException {
        String settings = loadClasspath(SETTINGS_PATH);
        String mapping  = loadClasspath(MAPPING_PATH);

        String body = String.format(
                "{\"settings\":%s,\"mappings\":%s}", settings, mapping);

        esClient.indices().create(r -> r
                .index(indexName)
                .withJson(new ByteArrayInputStream(body.getBytes(StandardCharsets.UTF_8))));
        log.info("[ES_ALIAS_REINDEX] 인덱스 생성: {}", indexName);
    }

    private String loadClasspath(String path) throws IOException {
        try (InputStream is = new ClassPathResource(path).getInputStream()) {
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    /** source → dest 전체 _reindex */
    private long callReindex(String source, String dest) throws IOException {
        String body = String.format(
                "{\"source\":{\"index\":\"%s\"},\"dest\":{\"index\":\"%s\"}}",
                source, dest);
        var resp = esClient.reindex(r -> r
                .withJson(new ByteArrayInputStream(body.getBytes(StandardCharsets.UTF_8))));
        return resp.total() != null ? resp.total() : 0L;
    }

    /**
     * reindex 시작 시점(since) 이후 updatedAt이 변경된 문서만 재색인.
     * _reindex 진행 중 발생한 쓰기 갭을 최소화.
     */
    private long deltaSync(String source, String dest, Instant since) throws IOException {
        // updatedAt 매핑 포맷 "uuuu-MM-dd'T'HH:mm:ss.SSS" 에 맞춰 포맷 (ISO_INSTANT의 'Z' 접미사는 거부됨)
        String sinceStr = DateTimeFormatter.ofPattern("uuuu-MM-dd'T'HH:mm:ss.SSS")
                .withZone(ZoneOffset.UTC)
                .format(since);
        String body = String.format(
                "{\"source\":{\"index\":\"%s\",\"query\":{\"range\":{\"updatedAt\":{\"gte\":\"%s\"}}}}," +
                "\"dest\":{\"index\":\"%s\"}}",
                source, sinceStr, dest);
        var resp = esClient.reindex(r -> r
                .withJson(new ByteArrayInputStream(body.getBytes(StandardCharsets.UTF_8))));
        return resp.total() != null ? resp.total() : 0L;
    }

    /** alias를 oldIndex에서 newIndex로 원자적으로 교체 */
    private void swapAlias(String oldIndex, String newIndex) throws IOException {
        // updateAliases builder에서 actions() 람다 두 번 호출 → 리스트에 append
        esClient.indices().updateAliases(r -> r
                .actions(a -> a.remove(rm -> rm.index(oldIndex).alias(ALIAS)))
                .actions(a -> a.add(add -> add.index(newIndex).alias(ALIAS)))
        );
    }
}
