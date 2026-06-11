package com.rocketcrew.pocat.global.config;

import com.zaxxer.hikari.HikariDataSource;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.datasource.LazyConnectionDataSourceProxy;

import javax.sql.DataSource;
import java.util.HashMap;
import java.util.Map;

/**
 * #206: RDS Read Replica 라우팅을 위한 DataSource 구성.
 *
 * <p>write/read 각각 별도 HikariCP 풀을 구성하고, {@link RoutingDataSource}로 트랜잭션의
 * readOnly 여부에 따라 라우팅한다. {@link LazyConnectionDataSourceProxy}로 감싸 실제 커넥션
 * 획득을 지연시킨다.
 *
 * <p>이 클래스가 {@code @Primary} {@link DataSource} 빈을 정의하므로
 * {@code DataSourceAutoConfiguration}은 비활성화된다(backoff). JPA EntityManagerFactory와
 * PlatformTransactionManager는 별도 빈 정의 없이 Spring Boot의 JpaBaseConfiguration이
 * 이 {@code @Primary} DataSource를 사용하여 자동 구성한다.
 *
 * TODO(#206): Replica는 prod 환경에 프로비저닝 완료(db.t4g.micro, pocat-slave 엔드포인트).
 *  Replica 연결 실패/미설정 환경(local/test)에서는 {@code spring.datasource.read.*}가 YAML
 *  cascading default(`${DB_READ_URL:${DB_URL}}` 등)로 write와 동일한 값을 사용해 안전하게
 *  fallback한다 (read=write fallback). open-in-view=false 전환 여부는 별도 후속 이슈로
 *  검토한다 (ADR-016).
 */
@Configuration
public class DataSourceConfig {

    @Bean
    @ConfigurationProperties("spring.datasource")
    public DataSourceProperties writeDataSourceProperties() {
        return new DataSourceProperties();
    }

    @Bean
    @ConfigurationProperties("spring.datasource.hikari")
    public HikariDataSource writeDataSource() {
        return writeDataSourceProperties()
                .initializeDataSourceBuilder()
                .type(HikariDataSource.class)
                .build();
    }

    @Bean
    @ConfigurationProperties("spring.datasource.read")
    public DataSourceProperties readDataSourceProperties() {
        return new DataSourceProperties();
    }

    @Bean
    @ConfigurationProperties("spring.datasource.read.hikari")
    public HikariDataSource readDataSource() {
        return readDataSourceProperties()
                .initializeDataSourceBuilder()
                .type(HikariDataSource.class)
                .build();
    }

    @Bean
    public DataSource routingDataSource(
            @Qualifier("writeDataSource") DataSource writeDataSource,
            @Qualifier("readDataSource") DataSource readDataSource) {
        RoutingDataSource routingDataSource = new RoutingDataSource();
        Map<Object, Object> targetDataSources = new HashMap<>();
        targetDataSources.put(DataSourceType.WRITE, writeDataSource);
        targetDataSources.put(DataSourceType.READ, readDataSource);
        routingDataSource.setTargetDataSources(targetDataSources);
        routingDataSource.setDefaultTargetDataSource(writeDataSource);
        return routingDataSource;
    }

    @Bean
    @Primary
    public DataSource dataSource(@Qualifier("routingDataSource") DataSource routingDataSource) {
        return new LazyConnectionDataSourceProxy(routingDataSource);
    }
}
