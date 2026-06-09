-- 1단계: 기존 배송 관련 상태를 새 상태로 마이그레이션
UPDATE orders 
SET status = 'ORDER_COMPLETED' 
WHERE status IN ('SHIPPING', 'SHIPPING_COMPLETED');

-- 2단계: ENUM 재정의 (배송 상태 제거)
ALTER TABLE orders
    MODIFY COLUMN status ENUM(
        'PAYMENT_PENDING',
        'DIRECT_PAYMENT_FAILED',
        'AUTO_PAYMENT_FAILED',
        'CANCELLED',
        'PAYMENT_COMPLETED',
        'ORDER_COMPLETED',
        'REFUNDED'
    ) NOT NULL;
