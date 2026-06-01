-- auction_snapshots 테이블이 없으면 스킵 (Hibernate ddl-auto가 올바른 스키마로 생성)
SET @sql = (
    SELECT IF(
        (SELECT COUNT(*) FROM information_schema.TABLES
         WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'auction_snapshots') = 0,
        'SELECT 1',
        IF(
            (SELECT COUNT(*) FROM information_schema.TABLE_CONSTRAINTS
             WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'auction_snapshots'
               AND CONSTRAINT_NAME = 'uk_auction_snapshots_auction_id') > 0,
            'ALTER TABLE auction_snapshots MODIFY COLUMN final_price BIGINT NULL',
            'ALTER TABLE auction_snapshots MODIFY COLUMN final_price BIGINT NULL, ADD CONSTRAINT uk_auction_snapshots_auction_id UNIQUE (auction_id)'
        )
    )
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
