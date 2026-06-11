package com.rocketcrew.pocat.global.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.jdbc.datasource.LazyConnectionDataSourceProxy;
import org.springframework.test.context.ActiveProfiles;
import com.rocketcrew.pocat.domain.auction.service.AuctionEsIndexService;
import com.rocketcrew.pocat.support.MockElasticsearchTestConfig;
import com.zaxxer.hikari.HikariDataSource;

import javax.sql.DataSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * #206: RDS Read Replica 라우팅 - DataSourceConfig Spring Context 통합 테스트
 *
 * <p>다음 빈들이 정상 등록되는지 검증한다:
 * <ul>
 *   <li>{@code routingDataSource} - {@link RoutingDataSource} 타입</li>
 *   <li>{@code dataSource} (@Primary) - {@link LazyConnectionDataSourceProxy} 타입</li>
 *   <li>{@code writeDataSource} / {@code readDataSource} - {@link HikariDataSource} 타입</li>
 * </ul>
 *
 * <p>{@code src/test/resources/application.yaml}의 {@code spring.datasource.read.*}는
 * write와 동일한 H2 URL을 가리키므로(fallback), 컨텍스트 로딩이 실패 없이 성공해야 한다.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("test")
@Import(MockElasticsearchTestConfig.class)
class DataSourceConfigTest {

    // Redis 인프라 빈을 @MockBean으로 교체 (실제 연결 방지)
    @MockBean
    private RedissonClient redissonClient;

    @MockBean
    private RedisConnectionFactory redisConnectionFactory;

    @MockBean
    private StringRedisTemplate stringRedisTemplate;

    @MockBean
    private RedisMessageListenerContainer redisMessageListenerContainer;

    @MockBean
    private AuctionEsIndexService auctionEsIndexService;

    @Autowired
    @org.springframework.beans.factory.annotation.Qualifier("dataSource")
    private DataSource dataSource;

    @Autowired
    @org.springframework.beans.factory.annotation.Qualifier("routingDataSource")
    private DataSource routingDataSource;

    @Autowired
    @org.springframework.beans.factory.annotation.Qualifier("writeDataSource")
    private DataSource writeDataSource;

    @Autowired
    @org.springframework.beans.factory.annotation.Qualifier("readDataSource")
    private DataSource readDataSource;

    @Test
    @DisplayName("routingDataSource 빈은 RoutingDataSource 타입으로 등록된다")
    void routingDataSource_isRoutingDataSourceType() {
        assertThat(routingDataSource).isInstanceOf(RoutingDataSource.class);
    }

    @Test
    @DisplayName("@Primary dataSource 빈은 LazyConnectionDataSourceProxy 타입으로 등록된다")
    void dataSource_isLazyConnectionDataSourceProxyType() {
        assertThat(dataSource).isInstanceOf(LazyConnectionDataSourceProxy.class);
    }

    @Test
    @DisplayName("writeDataSource / readDataSource 빈은 각각 HikariDataSource 타입으로 등록된다")
    void writeAndReadDataSources_areHikariDataSourceType() {
        assertThat(writeDataSource).isInstanceOf(HikariDataSource.class);
        assertThat(readDataSource).isInstanceOf(HikariDataSource.class);
    }
}
