package com.rocketcrew.pocat.global.config;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * #206: RDS Read Replica 라우팅 - RoutingDataSource 단위 테스트
 *
 * <p>{@code RoutingDataSource.determineCurrentLookupKey()}는 현재 트랜잭션의
 * readOnly 여부({@link TransactionSynchronizationManager#isCurrentTransactionReadOnly()})에 따라
 * {@link DataSourceType#READ} 또는 {@link DataSourceType#WRITE}를 반환해야 한다.
 */
class RoutingDataSourceTest {

    private final RoutingDataSource routingDataSource = new RoutingDataSource();

    @AfterEach
    void tearDown() {
        // 테스트 간 TransactionSynchronizationManager 상태 오염 방지
        TransactionSynchronizationManager.clear();
    }

    @Test
    @DisplayName("현재 트랜잭션이 readOnly=true이면 READ를 반환한다")
    void determineCurrentLookupKey_readOnlyTransaction_returnsRead() {
        // given
        TransactionSynchronizationManager.setActualTransactionActive(true);
        TransactionSynchronizationManager.setCurrentTransactionReadOnly(true);

        // when
        Object lookupKey = ReflectionTestUtils.invokeMethod(routingDataSource, "determineCurrentLookupKey");

        // then
        assertThat(lookupKey).isEqualTo(DataSourceType.READ);
    }

    @Test
    @DisplayName("현재 트랜잭션이 readOnly=false이면 WRITE를 반환한다")
    void determineCurrentLookupKey_writeTransaction_returnsWrite() {
        // given
        TransactionSynchronizationManager.setActualTransactionActive(true);
        TransactionSynchronizationManager.setCurrentTransactionReadOnly(false);

        // when
        Object lookupKey = ReflectionTestUtils.invokeMethod(routingDataSource, "determineCurrentLookupKey");

        // then
        assertThat(lookupKey).isEqualTo(DataSourceType.WRITE);
    }

    @Test
    @DisplayName("트랜잭션 컨텍스트가 없으면(readOnly 미설정) WRITE를 반환한다")
    void determineCurrentLookupKey_noTransaction_returnsWrite() {
        // when
        Object lookupKey = ReflectionTestUtils.invokeMethod(routingDataSource, "determineCurrentLookupKey");

        // then
        assertThat(lookupKey).isEqualTo(DataSourceType.WRITE);
    }
}
