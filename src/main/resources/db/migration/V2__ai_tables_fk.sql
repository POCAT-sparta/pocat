-- V2: ai_chat_sessions/messages 복수형 테이블 보정, FK 추가, 구 단수형 테이블 제거.
-- V1이 최종 스키마를 포함하므로 모든 DDL을 idempotent하게 처리.

-- ai_chat_sessions (V1에서 이미 생성됨, IF NOT EXISTS로 안전)
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

-- ai_chat_messages (V1에서 이미 생성됨, IF NOT EXISTS로 안전)
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

-- fk_ai_chat_messages_session: V1에서 이미 추가됐을 수 있으므로 idempotent 처리
SET @add_fk_msg = (
    SELECT IF(
        (SELECT COUNT(*) FROM information_schema.TABLE_CONSTRAINTS
         WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'ai_chat_messages'
           AND CONSTRAINT_NAME = 'fk_ai_chat_messages_session') > 0,
        'SELECT 1',
        'ALTER TABLE ai_chat_messages ADD CONSTRAINT fk_ai_chat_messages_session FOREIGN KEY (ai_chat_session_id) REFERENCES ai_chat_sessions(id) ON DELETE CASCADE'
    )
);
PREPARE stmt FROM @add_fk_msg;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- fk_card_ai_analysis_card: V1에서 이미 추가됐을 수 있으므로 idempotent 처리
SET @add_fk_analysis = (
    SELECT IF(
        (SELECT COUNT(*) FROM information_schema.TABLE_CONSTRAINTS
         WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'card_ai_analysis'
           AND CONSTRAINT_NAME = 'fk_card_ai_analysis_card') > 0,
        'SELECT 1',
        'ALTER TABLE card_ai_analysis ADD CONSTRAINT fk_card_ai_analysis_card FOREIGN KEY (card_id) REFERENCES cards(id) ON DELETE CASCADE'
    )
);
PREPARE stmt FROM @add_fk_analysis;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 구 단수형 테이블 제거 (V1에서 생성하지 않으므로 IF EXISTS로 안전)
DROP TABLE IF EXISTS ai_chat_message;
DROP TABLE IF EXISTS ai_chat_session;
