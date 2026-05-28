package com.rocketcrew.pocat.global.event;

import lombok.Getter;

import java.time.LocalDateTime;

@Getter
public abstract class BaseEvent {

    private final String eventType;
    private Long outboxId;
    private final LocalDateTime occurredAt;

    protected BaseEvent(String eventType) {
        this.eventType = eventType;
        this.occurredAt = LocalDateTime.now();
    }

    // outboxId 세팅
    public void bindOutboxId(Long outboxId) {
        if (this.outboxId != null) {
            // 이미 ID가 세팅되어 있다면 변경을 막는 방어 코드도 넣을 수 있습니다.
            throw new IllegalStateException("이미 아웃박스 ID가 할당된 이벤트입니다.");
        }
        this.outboxId = outboxId;
    }
}
