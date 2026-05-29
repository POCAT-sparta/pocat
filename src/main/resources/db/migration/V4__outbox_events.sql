CREATE TABLE IF NOT EXISTS outbox_events
(
    id            BIGINT       NOT NULL AUTO_INCREMENT,
    topic         VARCHAR(50)  NOT NULL,
    partition_key VARCHAR(100) NOT NULL,
    event_type    VARCHAR(100) NOT NULL,
    payload       TEXT         NOT NULL,
    status        VARCHAR(20)  NOT NULL,
    retry_count   INT          NOT NULL DEFAULT 0,
    created_at    DATETIME(6)  NOT NULL,
    processed_at  DATETIME(6),
    PRIMARY KEY (id),
    INDEX idx_outbox_status_created (status, created_at)
);
