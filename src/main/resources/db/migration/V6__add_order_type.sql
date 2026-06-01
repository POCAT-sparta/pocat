-- orders 테이블이 없으면 스킵 (Hibernate ddl-auto가 올바른 스키마로 생성)
-- order_type 컬럼이 이미 있어도 스킵
SET @sql = (
    SELECT IF(
        (SELECT COUNT(*) FROM information_schema.TABLES
         WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'orders') = 0
        OR
        (SELECT COUNT(*) FROM information_schema.COLUMNS
         WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'orders' AND COLUMN_NAME = 'order_type') > 0,
        'SELECT 1',
        'ALTER TABLE orders ADD COLUMN order_type VARCHAR(20) NOT NULL DEFAULT ''AUCTION'', ADD CONSTRAINT chk_orders_order_type CHECK (order_type IN (''AUCTION'', ''BUYOUT''))'
    )
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
