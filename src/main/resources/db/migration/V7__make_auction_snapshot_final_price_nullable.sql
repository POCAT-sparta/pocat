ALTER TABLE auction_snapshots
    MODIFY COLUMN final_price BIGINT NULL,
    ADD CONSTRAINT uk_auction_snapshots_auction_id UNIQUE (auction_id);
