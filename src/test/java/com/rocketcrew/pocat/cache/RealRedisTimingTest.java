package com.rocketcrew.pocat.cache;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.kafka.core.KafkaTemplate;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Tag;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * T-Redis: 실 Redis(Docker localhost:6379) 연결을 통한 SET/GET 응답 시간 측정.
 *
 * CachePerformanceTest 는 @MockBean StringRedisTemplate 을 클래스 레벨에 선언하여
 * StringRedisTemplate 을 Mock 으로 교체하기 때문에, 실 Redis 타이밍 측정은
 * 별도 클래스로 분리하여 진행한다.
 *
 * 전제 조건: Docker Desktop 실행 중, Redis 컨테이너 localhost:6379 리스닝.
 */
@Disabled("Requires live Redis on localhost:6379")
@Tag("bulk")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("test")
class RealRedisTimingTest {

    // Redis 인프라 중 StringRedisTemplate 은 실 연결을 사용하므로 MockBean 제외.
    // 나머지 Redis/AI/Kafka 인프라 빈은 Mock 처리하여 Context 기동 방지.
    @MockBean
    private RedissonClient redissonClient;

    @MockBean
    private RedisMessageListenerContainer redisMessageListenerContainer;

    // AI 인프라 Mock
    @MockBean
    private org.springframework.ai.chat.model.ChatModel chatModel;

    @MockBean
    private org.springframework.ai.embedding.EmbeddingModel embeddingModel;

    @MockBean
    private org.springframework.ai.vectorstore.VectorStore vectorStore;

    // Kafka Mock
    @MockBean(name = "kafkaTemplate")
    private KafkaTemplate<String, String> kafkaTemplate;

    @MockBean(name = "paymentKafkaTemplate")
    private KafkaTemplate<String, String> paymentKafkaTemplate;

    @MockBean(name = "refundKafkaTemplate")
    private KafkaTemplate<String, String> refundKafkaTemplate;

    @MockBean(name = "settlementKafkaTemplate")
    private KafkaTemplate<String, String> settlementKafkaTemplate;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Nested
    @DisplayName("실측: Redis 응답 시간 (ms)")
    class RealRedisTimingTests {

        @BeforeEach
        void cleanRedis() {
            stringRedisTemplate.delete("test:timing:key");
        }

        @Test
        @DisplayName("Redis SET/GET 응답 시간 측정")
        void redisSetGetLatency() {
            String key = "test:timing:key";
            String value = "test-value";

            // SET
            long setStart = System.nanoTime();
            stringRedisTemplate.opsForValue().set(key, value);
            long setMs = (System.nanoTime() - setStart) / 1_000_000;

            // GET (cache hit)
            long getStart = System.nanoTime();
            String result = stringRedisTemplate.opsForValue().get(key);
            long getMs = (System.nanoTime() - getStart) / 1_000_000;

            assertThat(result).isEqualTo(value);
            // Log for documentation
            System.out.printf("[Redis Timing] SET=%dms, GET=%dms%n", setMs, getMs);
            // Redis GET should be fast (< 100ms on local docker)
            assertThat(getMs).isLessThan(100L);
        }
    }
}
