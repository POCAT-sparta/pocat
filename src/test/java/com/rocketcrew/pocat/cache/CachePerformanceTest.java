package com.rocketcrew.pocat.cache;

import com.rocketcrew.pocat.domain.ai.analysis.dto.CardAnalysisResult;
import com.rocketcrew.pocat.domain.ai.analysis.entity.CardAiAnalysis;
import com.rocketcrew.pocat.domain.ai.analysis.repository.CardAiAnalysisRepository;
import com.rocketcrew.pocat.domain.ai.analysis.service.CardAnalysisService;
import com.rocketcrew.pocat.domain.ai.assistant.service.AiChatSessionService;
import com.rocketcrew.pocat.global.metrics.AiUsageMetrics;
import com.rocketcrew.pocat.domain.ai.prompt.service.AiPromptTemplateService;
import com.rocketcrew.pocat.domain.ai.rag.service.EmbeddingService;
import com.rocketcrew.pocat.domain.ai.rag.service.RagService;
import com.rocketcrew.pocat.domain.card.entity.Card;
import com.rocketcrew.pocat.domain.card.entity.enums.CardCategory;
import com.rocketcrew.pocat.domain.card.entity.enums.CardGrade;
import com.rocketcrew.pocat.domain.card.entity.enums.CardSource;
import com.rocketcrew.pocat.domain.card.entity.enums.CardStatus;
import com.rocketcrew.pocat.domain.card.repository.CardRepository;
import com.rocketcrew.pocat.domain.community.tradepost.dto.response.TradePostResponse;
import com.rocketcrew.pocat.domain.community.tradepost.entity.TradePost;
import com.rocketcrew.pocat.domain.community.tradepost.repository.TradePostRepository;
import com.rocketcrew.pocat.domain.community.tradepost.service.TradePostDetailCacheService;
import com.rocketcrew.pocat.domain.user.dto.response.UserResponse;
import com.rocketcrew.pocat.domain.user.entity.User;
import com.rocketcrew.pocat.domain.user.enums.UserRole;
import com.rocketcrew.pocat.domain.user.repository.UserRepository;
import com.rocketcrew.pocat.domain.user.service.UserQueryService;
import com.rocketcrew.pocat.support.TestFixtures;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.redisson.api.RedissonClient;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import com.rocketcrew.pocat.domain.card.repository.CardSearchRepository;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willDoNothing;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * C-01 / C-06 / cardAnalysis 캐시 성능 검증
 *
 * ConcurrentMapCacheManager(in-memory)를 사용하여 실제 Redis 없이
 * Spring Cache Abstraction(@Cacheable / @CacheEvict)의 DB 호출 횟수 감소 효과를 결정론적으로 검증.
 */
@Tag("bulk")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("test")
@Import(CachePerformanceTest.CachePerformanceTestConfig.class)
class CachePerformanceTest {

    @TestConfiguration
    static class CachePerformanceTestConfig {
        @Bean
        @Primary
        public CacheManager cacheManager() {
            return new ConcurrentMapCacheManager(
                    "user:profile", "user:bid-blocked",
                    "post:free:detail", "post:trade:detail",
                    "auction:bid-history", "post:comments",
                    "cardAnalysis"
            );
        }
    }

    // Redis 인프라 빈 Mock (실제 연결 방지)
    @MockBean
    private RedissonClient redissonClient;

    @MockBean
    private RedisConnectionFactory redisConnectionFactory;

    @MockBean
    private StringRedisTemplate stringRedisTemplate;

    @MockBean
    private RedisMessageListenerContainer redisMessageListenerContainer;

    // ES repo Mock (실제 ES 연결 방지)
    @MockBean
    private CardSearchRepository cardSearchRepository;

    // AI 인프라 빈 Mock
    @MockBean
    private ChatModel chatModel;

    @MockBean
    private EmbeddingModel embeddingModel;

    @MockBean
    private VectorStore vectorStore;

    // Kafka Mock (4개 빈 명시적 지정)
    @MockBean(name = "kafkaTemplate")
    private KafkaTemplate<String, String> kafkaTemplate;

    @MockBean(name = "paymentKafkaTemplate")
    private KafkaTemplate<String, String> paymentKafkaTemplate;

    @MockBean(name = "refundKafkaTemplate")
    private KafkaTemplate<String, String> refundKafkaTemplate;

    @MockBean(name = "settlementKafkaTemplate")
    private KafkaTemplate<String, String> settlementKafkaTemplate;

    @Autowired
    private UserQueryService userQueryService;

    @MockBean
    private UserRepository userRepository;

    @Autowired
    private TradePostDetailCacheService tradePostDetailCacheService;

    @MockBean
    private TradePostRepository tradePostRepository;

    @Autowired
    private CardAnalysisService cardAnalysisService;

    @MockBean
    private CardRepository cardRepository;

    @MockBean
    private AiPromptTemplateService promptTemplateService;

    @MockBean
    private AiUsageMetrics aiUsageMetrics;

    @MockBean
    private CardAiAnalysisRepository cardAiAnalysisRepository;

    @MockBean
    private ChatClient chatClient;

    @MockBean
    private RagService ragService;

    @MockBean
    private EmbeddingService embeddingService;

    @MockBean
    private AiChatSessionService aiChatSessionService;

    @Autowired
    private CacheManager cacheManager;

    @BeforeEach
    void clearAllCaches() {
        cacheManager.getCacheNames().forEach(name -> {
            Cache cache = cacheManager.getCache(name);
            if (cache != null) {
                cache.clear();
            }
        });
        clearInvocations(userRepository, tradePostRepository, cardRepository, chatClient);
    }

    // ---------------------------------------------------------------
    // C-01: user:profile 캐시
    // ---------------------------------------------------------------
    @Nested
    @DisplayName("C-01: user:profile 캐시")
    class UserProfileCache {

        @Test
        @DisplayName("동일 userId 2회 호출 시 DB 조회 1회만 발생")
        void cacheHit_reducesDbCalls() {
            // given: mock user (no real DB save needed)
            User mockUser = User.builder()
                    .email("cache-perf@pocat.com")
                    .password("encoded-pw")
                    .nickname("PerfTester")
                    .userRole(UserRole.USER)
                    .build();
            ReflectionTestUtils.setField(mockUser, "id", 1L);
            given(userRepository.findById(1L)).willReturn(Optional.of(mockUser));

            // when: 동일 userId로 2회 호출
            UserResponse first = userQueryService.getUserById(1L);
            UserResponse second = userQueryService.getUserById(1L);

            // then: Repository.findById()는 1회만 호출 (2번째는 캐시 히트)
            verify(userRepository, times(1)).findById(1L);
            assertThat(first).isNotNull();
            assertThat(second).isNotNull();
            assertThat(first.nickname()).isEqualTo(second.nickname());
        }
    }

    // ---------------------------------------------------------------
    // C-06: post:trade:detail 캐시
    // ---------------------------------------------------------------
    @Nested
    @DisplayName("C-06: post:trade:detail 캐시")
    class TradePostDetailCache {

        private static final Long POST_ID = 100L;
        private static final Long OWNER_ID = 1L;

        @BeforeEach
        void setUpPost() {
            User mockOwner = User.builder()
                    .email("owner@pocat.com")
                    .password("encoded-pw")
                    .nickname("PostOwner")
                    .userRole(UserRole.USER)
                    .build();
            ReflectionTestUtils.setField(mockOwner, "id", OWNER_ID);

            TradePost mockPost = TradePost.builder()
                    .userId(OWNER_ID)
                    .title("테스트 게시글")
                    .content("테스트 내용")
                    .price(10000L)
                    .thumbnail(null)
                    .viewCount(0)
                    .build();
            ReflectionTestUtils.setField(mockPost, "id", POST_ID);

            given(tradePostRepository.findById(POST_ID)).willReturn(Optional.of(mockPost));
            given(userRepository.findById(OWNER_ID)).willReturn(Optional.of(mockOwner));
        }

        @Test
        @DisplayName("캐시 히트 시 DB 조회 생략")
        void cacheHit_skipsDbQuery() {
            // when: 동일 postId로 2회 호출
            TradePostResponse first = tradePostDetailCacheService.loadPostDetail(POST_ID);
            TradePostResponse second = tradePostDetailCacheService.loadPostDetail(POST_ID);

            // then: 2번째는 캐시 히트 → tradePostRepository.findById() 1회만 호출
            verify(tradePostRepository, times(1)).findById(POST_ID);
            assertThat(first).isNotNull();
            assertThat(second).isNotNull();
            assertThat(first.title()).isEqualTo(second.title());
        }

        @Test
        @DisplayName("evict 후 캐시 무효화 → 다음 조회 시 DB 재조회")
        void cacheEvict_afterEvict_refreshesCache() {
            // 1st call: 캐시 미스 → 캐시 저장
            tradePostDetailCacheService.loadPostDetail(POST_ID);
            clearInvocations(tradePostRepository);

            // evict
            tradePostDetailCacheService.evict(POST_ID);

            // 2nd call after evict: 캐시 미스 → DB 재조회
            tradePostDetailCacheService.loadPostDetail(POST_ID);

            // then: evict 이후 DB 재조회 1회
            verify(tradePostRepository, times(1)).findById(POST_ID);
        }
    }

    // ---------------------------------------------------------------
    // cardAnalysis 캐시
    // ---------------------------------------------------------------
    @Nested
    @DisplayName("cardAnalysis 캐시")
    class CardAnalysisCache {

        private Card testCard;

        @BeforeEach
        void setUpCard() {
            // StringRedisTemplate mock — cache miss (no manual Redis result)
            org.springframework.data.redis.core.ValueOperations<String, String> valueOps =
                    org.mockito.Mockito.mock(
                            org.springframework.data.redis.core.ValueOperations.class);
            given(stringRedisTemplate.opsForValue()).willReturn(valueOps);
            given(valueOps.get(anyString())).willReturn(null);
            willDoNothing().given(aiUsageMetrics).recordUsage(anyInt(), anyInt(), anyLong(), anyString());
            given(cardAiAnalysisRepository.save(any(CardAiAnalysis.class)))
                    .willAnswer(inv -> inv.getArgument(0));

            // Stub prompt template
            given(promptTemplateService.getPrompt(anyString())).willReturn(
                    "카드 분석: {cardContext}\n{format}");

            // Stub ChatClient chain
            ChatClient.ChatClientRequestSpec requestSpec =
                    org.mockito.Mockito.mock(ChatClient.ChatClientRequestSpec.class,
                            org.mockito.Mockito.RETURNS_DEEP_STUBS);
            ChatClient.CallResponseSpec callSpec =
                    org.mockito.Mockito.mock(ChatClient.CallResponseSpec.class);
            given(chatClient.prompt(any(org.springframework.ai.chat.prompt.Prompt.class)))
                    .willReturn(requestSpec);
            given(requestSpec.call()).willReturn(callSpec);

            // LLM returns valid JSON
            String llmJson = "{\"priceTrend\":\"RISING\",\"fairValueEstimate\":150000,\"demandLevel\":\"HIGH\","
                    + "\"summary\":\"캐시 성능 테스트\",\"highlights\":[],\"riskFactors\":[],\"keywords\":[],"
                    + "\"analysisModel\":\"gemini-1.5-flash\",\"promptTokens\":100,\"completionTokens\":200,"
                    + "\"analyzedAt\":\"2026-05-28T00:00:00\"}";
            given(callSpec.content()).willReturn(llmJson);

            clearInvocations(cardRepository);
        }

        @Test
        @DisplayName("동일 cardId 2회 분석 요청 시 LLM 1회만 호출")
        void cacheHit_skipLlmCall() {
            // given: card in repository (use H2 real DB)
            testCard = Card.builder()
                    .userId(1L)
                    .name("피카츄")
                    .series(TestFixtures.aSeries())
                    .pokemonSet(TestFixtures.aPokemonSet())
                    .cardNumber("025")
                    .rarity("R")
                    .category(CardCategory.POKEMON)
                    .grade(CardGrade.PSA_10)
                    .source(CardSource.TCGDEX)
                    .status(CardStatus.ACTIVE)
                    .build();
            ReflectionTestUtils.setField(testCard, "id", 99L);
            given(cardRepository.findById(99L)).willReturn(Optional.of(testCard));
            Long cardId = 99L;

            // wire self-reference for reanalyzeCard
            ReflectionTestUtils.setField(cardAnalysisService, "self", cardAnalysisService);

            // when: 동일 cardId 2회 호출
            CardAnalysisResult first = cardAnalysisService.analyzeCard(cardId);
            CardAnalysisResult second = cardAnalysisService.analyzeCard(cardId);

            // then: ChatClient.prompt()는 1회만 호출되어야 한다 (2번째는 캐시 히트)
            verify(chatClient, times(1)).prompt(any(org.springframework.ai.chat.prompt.Prompt.class));
            assertThat(first).isNotNull();
            assertThat(second).isNotNull();
            assertThat(first.priceTrend()).isEqualTo(second.priceTrend());
        }
    }
}
