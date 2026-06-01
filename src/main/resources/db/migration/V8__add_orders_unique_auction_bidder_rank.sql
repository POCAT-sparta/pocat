-- (auction_id, bidder_rank) 중복 행 제거: 각 그룹에서 id가 가장 큰 행만 보존
DELETE FROM orders
WHERE id NOT IN (
    SELECT max_id FROM (
        SELECT MAX(id) AS max_id
        FROM orders
        GROUP BY auction_id, bidder_rank
    ) AS keep
);

-- ddl-auto:update 환경에서 이미 생성됐을 수 있으므로 조건부 추가
SET @add_constraint = (
    SELECT IF(
        COUNT(*) = 0,
        'ALTER TABLE orders ADD CONSTRAINT uk_auction_id_bidder_rank UNIQUE (auction_id, bidder_rank)',
        'SELECT 1'
    )
    FROM information_schema.TABLE_CONSTRAINTS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'orders'
      AND CONSTRAINT_NAME = 'uk_auction_id_bidder_rank'
);
PREPARE stmt FROM @add_constraint;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
