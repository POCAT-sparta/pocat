ALTER TABLE orders
    ADD COLUMN order_type VARCHAR(20) NOT NULL DEFAULT 'AUCTION',
    ADD CONSTRAINT chk_orders_order_type
        CHECK (order_type IN ('AUCTION', 'BUYOUT'));
