package com.rocketcrew.pocat.global.config;

import com.rocketcrew.pocat.cache.MockRedisTestConfig;
import com.rocketcrew.pocat.domain.auction.service.AuctionEsIndexService;
import com.rocketcrew.pocat.support.MockElasticsearchTestConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.orm.jpa.JpaProperties;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * #219: spring.jpa.open-in-view=false 전환 - RoutingDataSource 동작 + 설정 가드 통합 테스트
 *
 * <p>{@code RoutingDataSourceTest}(단위 테스트)는 {@code RoutingDataSource}를 직접 생성해
 * {@code determineCurrentLookupKey()}를 호출하지만, 이 테스트는 실제 Spring 트랜잭션 컨텍스트
 * 안에서 readOnly 트랜잭션 → 직후 별도 write 트랜잭션으로 전환될 때 각각 READ/WRITE로
 * "요청마다 재평가"되는지를 검증한다 (요청 내 1회 고정이 아님을 입증).
 *
 * <p>{@code DataSourceConfigTest}는 빈 타입 등록 여부만 검증하므로, 이 테스트와 책임이
 * 중복되지 않는다.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("test")
@Import({MockElasticsearchTestConfig.class, MockRedisTestConfig.class})
@DisplayName("open-in-view=false 환경에서 RoutingDataSource 라우팅 재평가 + JPA 설정 가드")
class OpenInViewRoutingIntegrationTest {

    @MockBean private RedissonClient redissonClient;
    @MockBean private RedisConnectionFactory redisConnectionFactory;
    @MockBean private StringRedisTemplate stringRedisTemplate;
    @MockBean private RedisMessageListenerContainer redisMessageListenerContainer;
    @MockBean private AuctionEsIndexService auctionEsIndexService;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private JpaProperties jpaProperties;

    private final RoutingDataSource routingDataSource = new RoutingDataSource();

    @Test
    @DisplayName("spring.jpa.open-in-view=false가 적용되어 있다 (T1 설정 가드)")
    void openInView_isDisabled() {
        assertThat(jpaProperties.getOpenInView())
                .as("OSIV가 비활성화되어 있어야 트랜잭션/영속성 컨텍스트 경계 밖에서의 지연 로딩 문제를 조기에 발견할 수 있다")
                .isFalse();
    }

    @Test
    @DisplayName("readOnly 트랜잭션 → READ, 직후 write 트랜잭션 → WRITE로 각각 재평가된다 (요청 내 1회 고정 아님)")
    void determineCurrentLookupKey_reevaluatesPerTransaction() {
        TransactionTemplate readOnlyTemplate = new TransactionTemplate(transactionManager);
        readOnlyTemplate.setReadOnly(true);

        TransactionTemplate writeTemplate = new TransactionTemplate(transactionManager);
        writeTemplate.setReadOnly(false);

        // 1) readOnly 트랜잭션 내부에서는 READ로 라우팅
        Object readLookupKey = readOnlyTemplate.execute(status ->
                ReflectionTestUtils.invokeMethod(routingDataSource, "determineCurrentLookupKey"));
        assertThat(readLookupKey).isEqualTo(DataSourceType.READ);

        // 2) 같은 스레드에서 곧바로 이어지는 write 트랜잭션은 WRITE로 라우팅 (1)의 결과에 고정되지 않음
        Object writeLookupKey = writeTemplate.execute(status ->
                ReflectionTestUtils.invokeMethod(routingDataSource, "determineCurrentLookupKey"));
        assertThat(writeLookupKey).isEqualTo(DataSourceType.WRITE);

        // 3) 다시 readOnly 트랜잭션으로 전환하면 다시 READ로 재평가
        Object readLookupKeyAgain = readOnlyTemplate.execute(status ->
                ReflectionTestUtils.invokeMethod(routingDataSource, "determineCurrentLookupKey"));
        assertThat(readLookupKeyAgain).isEqualTo(DataSourceType.READ);
    }

    @Test
    @DisplayName("트랜잭션 전파 속성과 무관하게 readOnly 플래그만으로 라우팅 키가 결정된다")
    void determineCurrentLookupKey_dependsOnlyOnReadOnlyFlag() {
        DefaultTransactionDefinition readOnlyDefinition = new DefaultTransactionDefinition();
        readOnlyDefinition.setReadOnly(true);

        TransactionTemplate template = new TransactionTemplate(transactionManager, readOnlyDefinition);

        Object lookupKey = template.execute(status ->
                ReflectionTestUtils.invokeMethod(routingDataSource, "determineCurrentLookupKey"));

        assertThat(lookupKey).isEqualTo(DataSourceType.READ);
    }
}
