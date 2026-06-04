-- ============================================================
-- k6 입찰 동시성 테스트용 시드 데이터
-- run.sh에서 자동 실행되므로 직접 실행할 필요 없음
--
-- 수동 실행:
--   docker exec -i pocat-db mysql -uroot -p${DB_PASSWORD} pocat \
--     < k6/setup/seed-bid-test-data.sql
-- ============================================================

-- 기존 테스트 입찰 기록 정리
DELETE FROM auction_bids
WHERE auction_id IN (SELECT id FROM auctions WHERE title = 'K6-BID-TEST-AUCTION');

-- 기존 테스트 경매 삭제
DELETE FROM auctions WHERE title = 'K6-BID-TEST-AUCTION';

-- 테스트 입찰자 5명 생성 (signup API rate limit 우회 — SQL 직접 INSERT)
-- 비밀번호: Test1234! (BCrypt 10 rounds)
-- INSERT IGNORE: 이미 존재하면 스킵 (재실행 안전)
INSERT IGNORE INTO users
    (email, password, nickname, user_role, is_bid_blocked, billing_key, unpaid_strike, created_at, updated_at)
VALUES
    ('k6-bidder-1@test.com', '$2a$10$7BfTzXreoPevDHissgC09udHtVX0k8Nt7Of5s/YI.aIlMq55sU3Sq', 'k6bidder1', 'USER', 0, 'k6-test-billing-key', 0, NOW(), NOW()),
    ('k6-bidder-2@test.com', '$2a$10$7BfTzXreoPevDHissgC09udHtVX0k8Nt7Of5s/YI.aIlMq55sU3Sq', 'k6bidder2', 'USER', 0, 'k6-test-billing-key', 0, NOW(), NOW()),
    ('k6-bidder-3@test.com', '$2a$10$7BfTzXreoPevDHissgC09udHtVX0k8Nt7Of5s/YI.aIlMq55sU3Sq', 'k6bidder3', 'USER', 0, 'k6-test-billing-key', 0, NOW(), NOW()),
    ('k6-bidder-4@test.com', '$2a$10$7BfTzXreoPevDHissgC09udHtVX0k8Nt7Of5s/YI.aIlMq55sU3Sq', 'k6bidder4', 'USER', 0, 'k6-test-billing-key', 0, NOW(), NOW()),
    ('k6-bidder-5@test.com', '$2a$10$7BfTzXreoPevDHissgC09udHtVX0k8Nt7Of5s/YI.aIlMq55sU3Sq', 'k6bidder5', 'USER', 0, 'k6-test-billing-key', 0, NOW(), NOW());

-- 기존 API로 생성된 유저의 billing_key 누락 보정 (이미 위 INSERT로 처리되나 안전장치)
UPDATE users SET billing_key = 'k6-test-billing-key'
WHERE email IN (
    'k6-bidder-1@test.com',
    'k6-bidder-2@test.com',
    'k6-bidder-3@test.com',
    'k6-bidder-4@test.com',
    'k6-bidder-5@test.com'
) AND (billing_key IS NULL OR billing_key = '');

-- ACTIVE 경매 삽입
-- started_at: 1시간 전 (Java가 과거로 읽도록 충분히 앞으로)
-- ended_at: +20시간 (KST +9h 오프셋 보정 + 1시간 여유 → Java는 1시간 후로 읽음)
INSERT INTO auctions
    (card_id, seller_id, title, starting_price, buyout_price,
     status, started_at, ended_at, created_at, updated_at)
VALUES
    (1, 1, 'K6-BID-TEST-AUCTION', 1000, 9999999,
     'ACTIVE',
     DATE_SUB(NOW(), INTERVAL 1 HOUR),
     -- 입찰 서비스는 LocalDateTime.now(Asia/Seoul) KST 기준으로 비교.
     -- JDBC가 DATETIME을 KST로 해석 후 UTC 변환 → Java는 stored-9h 로 읽음.
     -- KST now(=UTC+9)보다 크려면: stored-9 > UTC+9 → stored > UTC+18 → +20h 여유.
     DATE_ADD(NOW(), INTERVAL 20 HOUR),
     NOW(), NOW());

-- 삽입된 경매 ID 출력 (run.sh에서 AUCTION_ID로 캡처)
SELECT LAST_INSERT_ID() AS auction_id;
