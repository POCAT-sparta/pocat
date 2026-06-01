-- ============================================================
-- 동시성 테스트 재실행을 위한 초기화 스크립트
--
-- 실행:
--   docker exec -i pocat-db mysql -uroot -p${DB_PASSWORD} pocat \
--     < k6/setup/reset-order.sql
-- ============================================================

-- 테스트로 생성된 결제 기록 삭제
DELETE FROM payments
WHERE order_id IN (SELECT id FROM orders WHERE order_uid = 'K6-TEST-ORDER-001');

-- 주문 상태를 AUTO_PAYMENT_FAILED로 복구, deadline 1시간 연장
UPDATE orders
SET status           = 'AUTO_PAYMENT_FAILED',
    payment_deadline = DATE_ADD(NOW(), INTERVAL 10 HOUR),  -- KST 오프셋(+9h) + 여유(1h)
    updated_at       = NOW()
WHERE order_uid = 'K6-TEST-ORDER-001';

SELECT id, status, payment_deadline FROM orders WHERE order_uid = 'K6-TEST-ORDER-001';
