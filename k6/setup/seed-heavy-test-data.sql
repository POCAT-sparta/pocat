-- ============================================================
-- k6 중부하 / 실사용 시나리오용 시드 데이터
-- run-2.sh 에서 자동 실행됨 — 직접 실행할 필요 없음
--
-- 수동 실행:
--   docker exec -i pocat-db mysql -uroot -p${DB_PASSWORD} pocat \
--     < k6/setup/seed-heavy-test-data.sql
-- ============================================================

-- ── 1. 기존 K6 중부하 테스트 데이터 정리 ─────────────────────────
-- FOREIGN_KEY_CHECKS 일시 비활성화로 FK 순서 없이 안전하게 삭제
SET FOREIGN_KEY_CHECKS = 0;
DELETE FROM auction_bids     WHERE auction_id IN (SELECT id FROM (SELECT id FROM auctions WHERE title LIKE 'K6-HEAVY-%') t);
DELETE FROM auction_snapshots WHERE auction_id IN (SELECT id FROM (SELECT id FROM auctions WHERE title LIKE 'K6-HEAVY-%') t);
DELETE FROM auctions WHERE title LIKE 'K6-HEAVY-%';
SET FOREIGN_KEY_CHECKS = 1;

-- ── 2. 입찰자 5명 생성 (INSERT IGNORE: 이미 존재하면 스킵) ────────
-- 비밀번호: Test1234! (BCrypt 10 rounds)
INSERT IGNORE INTO users
    (email, password, nickname, user_role, is_bid_blocked, billing_key,
     unpaid_strike, created_at, updated_at)
VALUES
    ('k6-bidder-1@test.com', '$2a$10$7BfTzXreoPevDHissgC09udHtVX0k8Nt7Of5s/YI.aIlMq55sU3Sq',
     'k6bidder1', 'USER', 0, 'k6-test-billing-key', 0, NOW(), NOW()),
    ('k6-bidder-2@test.com', '$2a$10$7BfTzXreoPevDHissgC09udHtVX0k8Nt7Of5s/YI.aIlMq55sU3Sq',
     'k6bidder2', 'USER', 0, 'k6-test-billing-key', 0, NOW(), NOW()),
    ('k6-bidder-3@test.com', '$2a$10$7BfTzXreoPevDHissgC09udHtVX0k8Nt7Of5s/YI.aIlMq55sU3Sq',
     'k6bidder3', 'USER', 0, 'k6-test-billing-key', 0, NOW(), NOW()),
    ('k6-bidder-4@test.com', '$2a$10$7BfTzXreoPevDHissgC09udHtVX0k8Nt7Of5s/YI.aIlMq55sU3Sq',
     'k6bidder4', 'USER', 0, 'k6-test-billing-key', 0, NOW(), NOW()),
    ('k6-bidder-5@test.com', '$2a$10$7BfTzXreoPevDHissgC09udHtVX0k8Nt7Of5s/YI.aIlMq55sU3Sq',
     'k6bidder5', 'USER', 0, 'k6-test-billing-key', 0, NOW(), NOW());

-- billing_key 누락 보정 (API 가입 계정은 billing_key 가 비어 있을 수 있음)
UPDATE users
SET billing_key = 'k6-test-billing-key'
WHERE email LIKE 'k6-bidder-%@test.com'
  AND (billing_key IS NULL OR billing_key = '');

-- ── 3. 활성 경매 10개 삽입 (카드 1~8 분산) ───────────────────────
-- seller_id = 1: admin 계정 (Flyway 초기 데이터로 항상 존재)
-- ended_at: +20시간 → Java KST 기준으로 +11시간 여유
--   (JDBC DATETIME → KST 해석 → UTC 변환: stored_utc = NOW(UTC+0), Java reads stored_utc-9h as KST)
INSERT INTO auctions
    (card_id, seller_id, title, starting_price, buyout_price,
     status, started_at, ended_at, created_at, updated_at)
VALUES
    (1, 1, 'K6-HEAVY-1',   1000, 9999999, 'ACTIVE',
     DATE_SUB(NOW(), INTERVAL 1 HOUR), DATE_ADD(NOW(), INTERVAL 20 HOUR), NOW(), NOW()),
    (2, 1, 'K6-HEAVY-2',   2000, 9999999, 'ACTIVE',
     DATE_SUB(NOW(), INTERVAL 1 HOUR), DATE_ADD(NOW(), INTERVAL 20 HOUR), NOW(), NOW()),
    (3, 1, 'K6-HEAVY-3',   3000, 9999999, 'ACTIVE',
     DATE_SUB(NOW(), INTERVAL 1 HOUR), DATE_ADD(NOW(), INTERVAL 20 HOUR), NOW(), NOW()),
    (4, 1, 'K6-HEAVY-4',   1500, 9999999, 'ACTIVE',
     DATE_SUB(NOW(), INTERVAL 1 HOUR), DATE_ADD(NOW(), INTERVAL 20 HOUR), NOW(), NOW()),
    (5, 1, 'K6-HEAVY-5',   2500, 9999999, 'ACTIVE',
     DATE_SUB(NOW(), INTERVAL 1 HOUR), DATE_ADD(NOW(), INTERVAL 20 HOUR), NOW(), NOW()),
    (6, 1, 'K6-HEAVY-6',   5000, 9999999, 'ACTIVE',
     DATE_SUB(NOW(), INTERVAL 1 HOUR), DATE_ADD(NOW(), INTERVAL 20 HOUR), NOW(), NOW()),
    (7, 1, 'K6-HEAVY-7',   1000, 9999999, 'ACTIVE',
     DATE_SUB(NOW(), INTERVAL 1 HOUR), DATE_ADD(NOW(), INTERVAL 20 HOUR), NOW(), NOW()),
    (8, 1, 'K6-HEAVY-8',   3000, 9999999, 'ACTIVE',
     DATE_SUB(NOW(), INTERVAL 1 HOUR), DATE_ADD(NOW(), INTERVAL 20 HOUR), NOW(), NOW()),
    (1, 1, 'K6-HEAVY-9',   7000, 9999999, 'ACTIVE',
     DATE_SUB(NOW(), INTERVAL 1 HOUR), DATE_ADD(NOW(), INTERVAL 20 HOUR), NOW(), NOW()),
    (2, 1, 'K6-HEAVY-10',  9000, 9999999, 'ACTIVE',
     DATE_SUB(NOW(), INTERVAL 1 HOUR), DATE_ADD(NOW(), INTERVAL 20 HOUR), NOW(), NOW());

-- ── 4. 경매당 입찰 이력 50건 삽입 (쿼리 부하 현실화) ────────────────
-- 목적: auction_bids 테이블에 실제 데이터가 없으면 GET /bids 쿼리가
--       즉시 반환되어 HikariCP 커넥션을 거의 점유하지 않음.
--       50건/경매(총 500건)를 삽입하여 paginate 쿼리에 실질 I/O 부하 부여.
-- 입찰가: 100,000 ~ 1,050,000 원 범위 (k6 writer 최소가 2,000,000 보다 낮아 간섭 없음)
-- 상태: 모두 OUTBID (히스토리 데이터 — k6 실행 중 새 LEADING 입찰이 정상 생성됨)

SET @bid1 = (SELECT id FROM users WHERE email = 'k6-bidder-1@test.com' LIMIT 1);
SET @bid2 = (SELECT id FROM users WHERE email = 'k6-bidder-2@test.com' LIMIT 1);
SET @bid3 = (SELECT id FROM users WHERE email = 'k6-bidder-3@test.com' LIMIT 1);
SET @bid4 = (SELECT id FROM users WHERE email = 'k6-bidder-4@test.com' LIMIT 1);
SET @bid5 = (SELECT id FROM users WHERE email = 'k6-bidder-5@test.com' LIMIT 1);

INSERT INTO auction_bids (auction_id, user_id, bid_price, status, created_at, updated_at)
SELECT
    a.id AS auction_id,
    CASE (n.seq % 5)
        WHEN 0 THEN @bid1
        WHEN 1 THEN @bid2
        WHEN 2 THEN @bid3
        WHEN 3 THEN @bid4
        WHEN 4 THEN @bid5
    END AS user_id,
    100000 + (n.seq * 19000) AS bid_price,  -- 119,000 ~ 1,050,000
    'OUTBID' AS status,
    DATE_SUB(NOW(), INTERVAL (51 - n.seq) * 5 MINUTE) AS created_at,
    DATE_SUB(NOW(), INTERVAL (51 - n.seq) * 5 MINUTE) AS updated_at
FROM auctions a
CROSS JOIN (
    SELECT  1 AS seq UNION ALL SELECT  2 UNION ALL SELECT  3 UNION ALL SELECT  4 UNION ALL SELECT  5
    UNION ALL SELECT  6 UNION ALL SELECT  7 UNION ALL SELECT  8 UNION ALL SELECT  9 UNION ALL SELECT 10
    UNION ALL SELECT 11 UNION ALL SELECT 12 UNION ALL SELECT 13 UNION ALL SELECT 14 UNION ALL SELECT 15
    UNION ALL SELECT 16 UNION ALL SELECT 17 UNION ALL SELECT 18 UNION ALL SELECT 19 UNION ALL SELECT 20
    UNION ALL SELECT 21 UNION ALL SELECT 22 UNION ALL SELECT 23 UNION ALL SELECT 24 UNION ALL SELECT 25
    UNION ALL SELECT 26 UNION ALL SELECT 27 UNION ALL SELECT 28 UNION ALL SELECT 29 UNION ALL SELECT 30
    UNION ALL SELECT 31 UNION ALL SELECT 32 UNION ALL SELECT 33 UNION ALL SELECT 34 UNION ALL SELECT 35
    UNION ALL SELECT 36 UNION ALL SELECT 37 UNION ALL SELECT 38 UNION ALL SELECT 39 UNION ALL SELECT 40
    UNION ALL SELECT 41 UNION ALL SELECT 42 UNION ALL SELECT 43 UNION ALL SELECT 44 UNION ALL SELECT 45
    UNION ALL SELECT 46 UNION ALL SELECT 47 UNION ALL SELECT 48 UNION ALL SELECT 49 UNION ALL SELECT 50
) n
WHERE a.title LIKE 'K6-HEAVY-%';

-- ── 5. 삽입된 경매 ID 목록 반환 (콤마 구분, run-2.sh 에서 AUCTION_IDS 로 파싱) ──
SELECT GROUP_CONCAT(id ORDER BY id SEPARATOR ',') AS auction_ids
FROM auctions
WHERE title LIKE 'K6-HEAVY-%';
