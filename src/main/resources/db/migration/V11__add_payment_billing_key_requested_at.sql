-- V11: billing_key_requested_at 컬럼 추가 (V1에 이미 포함 — idempotent 처리)
SET @add_col = (
    SELECT IF(
        (SELECT COUNT(*) FROM information_schema.COLUMNS
         WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'payments'
           AND COLUMN_NAME = 'billing_key_requested_at') > 0,
        'SELECT 1',
        'ALTER TABLE payments ADD COLUMN billing_key_requested_at datetime(6) NULL'
    )
);
PREPARE stmt FROM @add_col;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
