package com.rocketcrew.pocat.global.event;

import lombok.Getter;

import java.time.LocalDateTime;

@Getter
public abstract class BaseEvent {

    private final String eventType;
    private final LocalDateTime occurredAt;

    protected BaseEvent(String eventType) {
        this.eventType = eventType;
        this.occurredAt = LocalDateTime.now();
    }
}
