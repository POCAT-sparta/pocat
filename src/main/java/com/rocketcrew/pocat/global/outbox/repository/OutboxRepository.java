package com.rocketcrew.pocat.global.outbox.repository;

import com.rocketcrew.pocat.global.outbox.enums.OutboxStatus;
import com.rocketcrew.pocat.global.outbox.entity.OutboxEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface OutboxRepository extends JpaRepository<OutboxEvent, Long> {

    List<OutboxEvent> findTop100ByStatusOrderByCreatedAtAsc(OutboxStatus status);

    boolean existsByTopicAndPartitionKeyAndEventTypeAndStatusIn(
            String topic, String partitionKey, String eventType, Collection<OutboxStatus> statuses);

    // 조건부 UPDATE (선점용)
    @Modifying
    @Query("UPDATE OutboxEvent o SET o.status = :to " +
            "WHERE o.id = :id AND o.status = :from")
    int markProcessingIfPending(
            @Param("id") Long id,
            @Param("from") OutboxStatus from,
            @Param("to") OutboxStatus to
    );
}
