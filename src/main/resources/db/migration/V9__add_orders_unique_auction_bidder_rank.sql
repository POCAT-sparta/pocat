-- orders 테이블이 없으면 스킵 (Hibernate ddl-auto가 올바른 스키마로 생성)
SET @cleanup = (
    SELECT IF(
        (SELECT COUNT(*) FROM information_schema.TABLES
         WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'orders') = 0,
        'SELECT 1',
        'DELETE FROM orders WHERE bidder_rank IS NOT NULL AND id NOT IN (SELECT max_id FROM (SELECT MAX(id) AS max_id FROM orders WHERE bidder_rank IS NOT NULL GROUP BY auction_id, bidder_rank) AS keep)'
    )
);
PREPARE stmt FROM @cleanup;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- orders 테이블이 없거나 제약이 이미 있으면 스킵
SET @add_constraint = (
    SELECT IF(
        (SELECT COUNT(*) FROM information_schema.TABLES
         WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'orders') = 0,
        'SELECT 1',
        IF(
            (SELECT COUNT(*) FROM information_schema.TABLE_CONSTRAINTS
             WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'orders'
               AND CONSTRAINT_NAME = 'uk_auction_id_bidder_rank') > 0,
            'SELECT 1',
            'ALTER TABLE orders ADD CONSTRAINT uk_auction_id_bidder_rank UNIQUE (auction_id, bidder_rank)'
        )
    )
);
PREPARE stmt FROM @add_constraint;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
