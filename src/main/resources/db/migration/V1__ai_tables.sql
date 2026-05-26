CREATE TABLE IF NOT EXISTS ai_prompt_template (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    card_grade VARCHAR(20) NOT NULL,
    prompt_text TEXT NOT NULL,
    version INT NOT NULL DEFAULT 1,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    deleted_at DATETIME(6)
);

CREATE TABLE IF NOT EXISTS card_ai_analysis (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    card_id BIGINT NOT NULL,
    price_trend VARCHAR(10) NOT NULL,
    fair_value_estimate BIGINT,
    demand_level VARCHAR(10) NOT NULL,
    summary TEXT,
    highlights TEXT,
    risk_factors TEXT,
    keywords TEXT,
    analysis_model VARCHAR(100),
    prompt_tokens INT,
    completion_tokens INT,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    deleted_at DATETIME(6),
    INDEX idx_card_ai_analysis_card_id (card_id)
);

CREATE TABLE IF NOT EXISTS ai_chat_session (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    session_uuid VARCHAR(36) NOT NULL UNIQUE,
    user_id BIGINT NOT NULL,
    total_tokens BIGINT NOT NULL DEFAULT 0,
    last_active_at DATETIME(6) NOT NULL,
    is_expired BOOLEAN NOT NULL DEFAULT FALSE,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    deleted_at DATETIME(6),
    INDEX idx_ai_chat_session_user_id (user_id),
    INDEX idx_ai_chat_session_last_active (last_active_at)
);

CREATE TABLE IF NOT EXISTS ai_chat_message (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    session_id BIGINT NOT NULL,
    role VARCHAR(20) NOT NULL,
    content TEXT NOT NULL,
    token_count INT NOT NULL DEFAULT 0,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    deleted_at DATETIME(6),
    INDEX idx_ai_chat_message_session_id (session_id)
);
