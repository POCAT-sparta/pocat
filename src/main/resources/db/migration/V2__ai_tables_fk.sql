-- V2: Ensure proper plural-named tables exist, add FK constraints, remove orphaned V1 singular tables

-- Ensure plural tables exist (matching JPA entity table names with correct schema)
CREATE TABLE IF NOT EXISTS ai_chat_sessions (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    session_uuid VARCHAR(36) NOT NULL UNIQUE,
    total_tokens INT NOT NULL DEFAULT 0,
    is_expired BOOLEAN NOT NULL DEFAULT FALSE,
    last_active_at DATETIME(6) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    deleted_at DATETIME(6),
    INDEX idx_ai_chat_session_user_id (user_id),
    INDEX idx_ai_chat_session_uuid (session_uuid),
    INDEX idx_ai_chat_session_last_active (last_active_at)
);

CREATE TABLE IF NOT EXISTS ai_chat_messages (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    ai_chat_session_id BIGINT NOT NULL,
    role VARCHAR(20) NOT NULL,
    content LONGTEXT NOT NULL,
    token_count INT NOT NULL DEFAULT 0,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    deleted_at DATETIME(6),
    INDEX idx_ai_chat_message_session_id (ai_chat_session_id),
    INDEX idx_ai_chat_message_created_at (created_at)
);

-- Add FK constraints (Hibernate ddl-auto does not add FKs automatically)
-- Note: FK to users is intentionally omitted here.
-- users table is managed by Hibernate (ddl-auto:update) which runs AFTER Flyway,
-- so the users table does not yet exist when this migration executes on a fresh DB.

ALTER TABLE ai_chat_messages
    ADD CONSTRAINT fk_ai_chat_messages_session FOREIGN KEY (ai_chat_session_id) REFERENCES ai_chat_sessions(id) ON DELETE CASCADE;

ALTER TABLE card_ai_analysis
    ADD CONSTRAINT fk_card_ai_analysis_card FOREIGN KEY (card_id) REFERENCES cards(id) ON DELETE CASCADE;

-- Drop V1 orphaned singular tables (never used by JPA entities; services use ai_chat_sessions/ai_chat_messages)
DROP TABLE IF EXISTS ai_chat_message;
DROP TABLE IF EXISTS ai_chat_session;
