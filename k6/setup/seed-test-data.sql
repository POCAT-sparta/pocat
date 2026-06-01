-- ============================================================
-- k6 결제 동시성 테스트용 시드 데이터
-- run.sh에서 자동 실행되므로 직접 실행할 필요 없음
--
-- 수동 실행:
--   docker exec -i pocat-db mysql -uroot -p${DB_PASSWORD} pocat \
--     < k6/setup/seed-test-data.sql
-- ============================================================

-- 기존 테스트 결제 기록 정리 (외래키 순서 고려)
DELETE FROM payments
WHERE order_id IN (SELECT id FROM orders WHERE order_uid = 'K6-TEST-ORDER-001');

-- 기존 테스트 주문 삭제
DELETE FROM orders WHERE order_uid = 'K6-TEST-ORDER-001';

-- 테스트 주문 삽입
-- buyer_id : 아래 @buyer_id 변수 (k6-buyer@test.com 회원가입 후 결정됨)
-- card_id  : 시드 데이터 카드 1번 사용
-- status   : AUTO_PAYMENT_FAILED (generatePayment 진입 조건)
-- payment_deadline : 현재 시각 + 10시간 (KST +9h 오프셋 보정 + 1시간 여유)
SET @buyer_id = (SELECT id FROM users WHERE email = 'k6-buyer@test.com' LIMIT 1);

INSERT INTO orders
    (card_id, seller_id, buyer_id, order_uid,
     final_price, status, delivery_status,
     payment_deadline, order_type, created_at, updated_at)
VALUES
    (1, 1, @buyer_id, 'K6-TEST-ORDER-001',
     10000, 'AUTO_PAYMENT_FAILED', 'PREPARING',
     -- serverTimezone=Asia/Seoul(KST=UTC+9) 설정으로 JDBC가 DATETIME을 KST로 해석 후 UTC 변환.
     -- Java가 "NOW()+1시간(UTC)" 으로 읽으려면 DB에는 "UTC + 9시간(오프셋) + 1시간(여유)" = +10시간 저장.
     DATE_ADD(NOW(), INTERVAL 10 HOUR), 'AUCTION', NOW(), NOW());

-- 삽입된 주문 ID 출력 (run.sh에서 ORDER_ID로 캡처)
SELECT LAST_INSERT_ID() AS order_id;
