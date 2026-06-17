package com.rocketcrew.pocat.domain.card.service;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import com.rocketcrew.pocat.domain.auction.kafka.AuctionEventHandler;
import com.rocketcrew.pocat.domain.auction.repository.AuctionSearchRepository;
import com.rocketcrew.pocat.domain.auction.service.AuctionEsIndexService;
import com.rocketcrew.pocat.domain.bid.service.BidEventHandler;
import com.rocketcrew.pocat.domain.auction.service.AuctionQueryService;
import com.rocketcrew.pocat.domain.card.repository.CardSearchRepository;
import com.rocketcrew.pocat.domain.card.service.CardQueryService;
import com.rocketcrew.pocat.domain.notification.service.NotificationEventHandler;
import com.rocketcrew.pocat.domain.order.service.OrderEventHandler;
import com.rocketcrew.pocat.domain.payment.client.out.kafka.handler.PaymentEventHandler;
import com.rocketcrew.pocat.domain.payment.client.out.portone.PortOneClientService;
import com.rocketcrew.pocat.domain.refund.service.RefundEventHandler;
import com.rocketcrew.pocat.global.config.EsIndexInitializer;
import com.rocketcrew.pocat.global.dto.EsReindexResponse;
import com.rocketcrew.pocat.global.outbox.service.OutboxEventWriter;
import com.rocketcrew.pocat.global.ratelimit.RedisRateLimiter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import com.rocketcrew.pocat.domain.auction.redis.AuctionExpirationRedisSubscriber;
import com.rocketcrew.pocat.domain.order.service.ExpiryEventListener;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.elasticsearch.ElasticsearchContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * CardEsAliasReindexService 통합 테스트
 *
 * 실제 Elasticsearch(Testcontainers)를 사용해 alias 세팅과 무중단 재인덱싱 흐름을 검증한다.
 * 외부 의존성(Redis, Kafka, PortOne, DB)은 @MockBean으로 격리.
 *
 * 매핑 변경 시나리오:
 * 1. es-alias-setup → cards_v1 생성 + alias 연결
 * 2. 운영 중 CardDocument 매핑 변경 (새 필드 추가 등)
 * 3. es-reindex → cards_v2(새 매핑) 생성 + 데이터 복사 + alias 교체
 */
@Tag("integration")
@Testcontainers
// spring.autoconfigure.exclude= : test/application.yaml이 ES 자동구성 3개를 제외하고 있어
// 이 테스트에서만 해제. @DynamicPropertySource가 Testcontainer URL을 spring.elasticsearch.uris에 주입.
@SpringBootTest(properties = "spring.autoconfigure.exclude=")
@DisplayName("카드 ES Alias 재인덱싱 통합 테스트")
class CardEsAliasReindexServiceIntegrationTest {

    // ── Elasticsearch Testcontainer ───────────────────────────────
    @Container
    static ElasticsearchContainer elasticsearch =
            new ElasticsearchContainer(
                    DockerImageName.parse("docker.elastic.co/elasticsearch/elasticsearch:8.16.0")
                            .asCompatibleSubstituteFor("docker.elastic.co/elasticsearch/elasticsearch"))
                    .withEnv("xpack.security.enabled", "false")
                    .withEnv("discovery.type", "single-node");

    @DynamicPropertySource
    static void overrideEsProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.elasticsearch.uris",
                () -> "http://" + elasticsearch.getHttpHostAddress());
    }

    // ── 테스트 대상 ─────────────────────────────────────────────────
    @Autowired private CardEsAliasReindexService service;
    @Autowired private ElasticsearchClient esClient;

    // ElasticsearchOperations 의존 빈 — Spring AI BOM 충돌로 실제 빈 생성 불가 → Mock 처리
    @MockBean private CardSearchRepository cardSearchRepository;
    @MockBean private CardQueryService cardQueryService;
    @MockBean private AuctionQueryService auctionQueryService;

    // ── 자동 초기화 방지 ────────────────────────────────────────────
    @MockBean private EsIndexInitializer esIndexInitializer;

    // ── 외부 인프라 Mock (기존 통합 테스트 패턴 동일) ────────────────
    @MockBean private StringRedisTemplate stringRedisTemplate;
    @MockBean private RedissonClient redissonClient;
    @MockBean private RedisRateLimiter redisRateLimiter;
    @MockBean private PortOneClientService portOneClientService;
    @MockBean private OutboxEventWriter outboxEventWriter;
    @MockBean private AuctionSearchRepository auctionSearchRepository;
    @MockBean private AuctionEsIndexService auctionEsIndexService;
    @MockBean private RedisConnectionFactory redisConnectionFactory;
    @MockBean private RedisMessageListenerContainer redisMessageListenerContainer;
    @MockBean private AuctionExpirationRedisSubscriber auctionExpirationRedisSubscriber;
    @MockBean private ExpiryEventListener expiryEventListener;
    @MockBean private AuctionEventHandler auctionEventHandler;
    @MockBean private BidEventHandler bidEventHandler;
    @MockBean private OrderEventHandler orderEventHandler;
    @MockBean private PaymentEventHandler paymentEventHandler;
    @MockBean private RefundEventHandler refundEventHandler;
    @MockBean private NotificationEventHandler notificationEventHandler;

    // ── 공통 정리 ───────────────────────────────────────────────────

    @BeforeEach
    void cleanUpIndices() {
        // 각 테스트 전 cards 관련 인덱스 전부 삭제 → 독립된 상태 보장
        silentDelete("cards");
        silentDelete("cards_v1");
        silentDelete("cards_v2");
        silentDelete("cards_v3");
    }

    // ── 테스트 ────────────────────────────────────────────────────

    @Nested
    @DisplayName("setupAlias()")
    class SetupAlias {

        @Test
        @DisplayName("cards 직접 인덱스 → cards_v1 + alias 교체")
        void migratesDirectIndexToAlias() throws IOException {
            // given: cards 직접 인덱스 생성 + 문서 2건 색인
            indexCard("cards", "1");
            indexCard("cards", "2");
            refresh("cards");

            // when
            EsReindexResponse response = service.setupAlias();

            // then: 응답 확인
            assertThat(response.previousIndex()).isEqualTo("cards");
            assertThat(response.newIndex()).isEqualTo("cards_v1");
            assertThat(response.documentCount()).isEqualTo(2L);

            // alias 존재 확인
            boolean aliasExists = esClient.indices()
                    .existsAlias(r -> r.name("cards")).value();
            assertThat(aliasExists).isTrue();

            // alias target = cards_v1
            assertThat(getAliasTarget("cards")).isEqualTo("cards_v1");
        }

        @Test
        @DisplayName("이미 alias가 존재하면 스킵")
        void skipsIfAliasAlreadyExists() throws IOException {
            // given: alias 세팅 먼저 실행
            service.setupAlias();

            // when: 다시 실행
            EsReindexResponse response = service.setupAlias();

            // then: 스킵 메시지
            assertThat(response.message()).contains("이미 존재");
        }

        @Test
        @DisplayName("인덱스/alias 없으면 빈 cards_v1 + alias 생성")
        void createsEmptyV1WhenNothingExists() {
            // given: cleanUp으로 아무것도 없는 상태

            // when
            EsReindexResponse response = service.setupAlias();

            // then
            assertThat(response.newIndex()).isEqualTo("cards_v1");
            assertThat(response.documentCount()).isEqualTo(0L);
        }

        @Test
        @DisplayName("cards_v1이 이미 존재할 때 alias만 연결하고 데이터를 보존한다 (부분 실패 재시도)")
        void reconnectsAliasWhenV1AlreadyExists() throws IOException {
            // given: 이전 setupAlias()가 cards_v1 생성 후 alias 연결 전에 실패한 상태
            indexCard("cards_v1", "99");
            refresh("cards_v1");

            // when: 재시도
            EsReindexResponse response = service.setupAlias();

            // then: 예외 없이 alias 연결 완료
            boolean aliasExists = esClient.indices()
                    .existsAlias(r -> r.name("cards")).value();
            assertThat(aliasExists).isTrue();
            assertThat(getAliasTarget("cards")).isEqualTo("cards_v1");

            // cards_v1의 기존 데이터 보존 확인
            refresh("cards");
            long count = esClient.count(r -> r.index("cards")).count();
            assertThat(count).isEqualTo(1L);
        }
    }

    @Nested
    @DisplayName("reindex() — 무중단 재인덱싱")
    class Reindex {

        @Test
        @DisplayName("cards_v1 → cards_v2: alias 교체, 데이터 보존, 구 인덱스 삭제")
        void swapsAliasAndPreservesData() throws IOException {
            // given: alias 세팅 + 문서 3건 색인
            service.setupAlias();   // cards_v1 + alias "cards"
            indexCard("cards", "10");
            indexCard("cards", "11");
            indexCard("cards", "12");
            refresh("cards");

            // when: 매핑 변경 후 재인덱싱 시뮬레이션
            EsReindexResponse response = service.reindex();

            // then: 응답
            assertThat(response.previousIndex()).isEqualTo("cards_v1");
            assertThat(response.newIndex()).isEqualTo("cards_v2");
            assertThat(response.documentCount()).isGreaterThanOrEqualTo(3L);

            // alias target 변경 확인
            assertThat(getAliasTarget("cards")).isEqualTo("cards_v2");

            // 구 인덱스 삭제 확인
            boolean v1Exists = esClient.indices()
                    .exists(r -> r.index("cards_v1")).value();
            assertThat(v1Exists).isFalse();

            // 새 인덱스에서 데이터 조회
            refresh("cards");
            long docCount = esClient.count(r -> r.index("cards")).count();
            assertThat(docCount).isGreaterThanOrEqualTo(3L);
        }

        @Test
        @DisplayName("2회 연속 reindex: cards_v1 → v2 → v3")
        void incrementsVersionOnEachReindex() throws IOException {
            // given
            service.setupAlias();   // v1
            indexCard("cards", "20");
            refresh("cards");

            // when
            service.reindex();      // v1 → v2
            EsReindexResponse second = service.reindex(); // v2 → v3

            // then
            assertThat(second.previousIndex()).isEqualTo("cards_v2");
            assertThat(second.newIndex()).isEqualTo("cards_v3");
            assertThat(getAliasTarget("cards")).isEqualTo("cards_v3");

            boolean v1Exists = esClient.indices().exists(r -> r.index("cards_v1")).value();
            boolean v2Exists = esClient.indices().exists(r -> r.index("cards_v2")).value();
            assertThat(v1Exists).isFalse();
            assertThat(v2Exists).isFalse();
        }

        @Test
        @DisplayName("alias 없이 reindex() 호출 시 예외")
        void throwsIfAliasNotSetup() {
            // given: setupAlias() 미실행 상태

            // when/then
            assertThatThrownBy(() -> service.reindex())
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("alias");
        }
    }

    // ── helpers ────────────────────────────────────────────────────

    /** 지정 인덱스에 최소 문서를 직접 색인 (ElasticsearchOperations 불필요) */
    private void indexCard(String index, String id) throws IOException {
        String json = String.format(
                "{\"userId\":1,\"name\":\"Pikachu-%s\",\"nameKo\":\"피카츄-%s\"," +
                "\"status\":\"ACTIVE\",\"updatedAt\":\"2026-06-09T00:00:00.000\"}",
                id, id);
        esClient.index(i -> i
                .index(index)
                .id(id)
                .withJson(new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8))));
    }

    private void refresh(String index) {
        try {
            esClient.indices().refresh(r -> r.index(index));
        } catch (Exception ignored) {}
    }

    private String getAliasTarget(String aliasName) throws IOException {
        return esClient.indices().getAlias(r -> r.name(aliasName))
                .result().keySet().stream()
                .findFirst()
                .orElseThrow();
    }

    private void silentDelete(String index) {
        try {
            esClient.indices().delete(r -> r.index(index));
        } catch (Exception ignored) {}
    }
}
