-- ============================================================
-- 테스트 경매 시드 SQL (MySQL)
-- 실행 전제: DataInit이 먼저 실행되어 user1@test.com 이 존재해야 함
-- 사용법:
--   docker exec -i <mysql-container-name> mysql -u root -proot pocat < scripts/seed-auctions.sql
-- ============================================================

-- 1. 테스트 유저 추가 (비밀번호: test1234, user1@test.com과 동일한 해시 재사용)
INSERT IGNORE INTO users (
    email,
    password,
    nickname,
    user_role,
    billing_key,
    unpaid_strike,
    is_bid_blocked,
    created_at,
    updated_at
)
SELECT
    'user3@test.com',
    password,
    '테스트유저3',
    'USER',
    'test-billing-key-3',
    0,
    FALSE,
    NOW(),
    NOW()
FROM users
WHERE email = 'user1@test.com'
LIMIT 1;

-- 2. 경매 추가 — ACTIVE 카드 중 id 오름차순으로 5장 참조
--    이미 ACTIVE 경매가 있는 카드는 중복 방지로 제외
INSERT INTO auctions (
    card_id,
    seller_id,
    title,
    description,
    starting_price,
    buyout_price,
    status,
    started_at,
    ended_at,
    created_at,
    updated_at
)
SELECT
    c.id,
    (SELECT id FROM users WHERE email = 'user1@test.com' LIMIT 1),
    CONCAT(c.name, ' 경매'),
    '테스트 경매입니다.',
    5000,
    50000,
    'ACTIVE',
    NOW(),
    DATE_ADD(NOW(), INTERVAL 7 DAY),
    NOW(),
    NOW()
FROM cards c
WHERE c.status = 'ACTIVE'
  AND c.deleted_at IS NULL
  AND NOT EXISTS (
      SELECT 1
      FROM auctions a
      WHERE a.card_id = c.id
        AND a.status = 'ACTIVE'
        AND a.deleted_at IS NULL
  )
ORDER BY c.id
LIMIT 5;

SELECT CONCAT('경매 시드 완료: ', ROW_COUNT(), '건 추가') AS result;
