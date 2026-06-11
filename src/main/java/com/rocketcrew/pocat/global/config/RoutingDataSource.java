package com.rocketcrew.pocat.global.config;

import org.springframework.jdbc.datasource.LazyConnectionDataSourceProxy;
import org.springframework.jdbc.datasource.lookup.AbstractRoutingDataSource;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * #206: 현재 트랜잭션의 readOnly 여부에 따라 WRITE/READ DataSource를 라우팅한다.
 *
 * <p>{@link LazyConnectionDataSourceProxy}와 함께 사용해야 한다 — 실제 커넥션 획득을
 * 트랜잭션 동기화(readOnly 플래그 확정) 이후로 지연시켜야 라우팅 키가 올바르게 결정된다.
 *
 * TODO(#206): Replica 프로비저닝 완료(db.t4g.micro, pocat-slave) — readOnly 라우팅
 *  통합 테스트는 여전히 후속 작업.
 *  특히 PaymentQueryService의 FOR UPDATE 락 메서드(findPaymentByIdWithLock 등)는
 *  클래스 레벨 readOnly=true를 상속하지만, 모든 호출자가 write 트랜잭션이므로
 *  cross-bean join 시 WRITE로 라우팅됨 — Replica 환경에서 실제 검증 필요 (ADR-016 참고).
 */
public class RoutingDataSource extends AbstractRoutingDataSource {

    @Override
    protected Object determineCurrentLookupKey() {
        return TransactionSynchronizationManager.isCurrentTransactionReadOnly()
                ? DataSourceType.READ
                : DataSourceType.WRITE;
    }
}
