-- Tracks whether a billing-key payment POST has been claimed/sent.
-- Kafka redelivery must not issue another billing-key POST for the same payment.

ALTER TABLE payments
    ADD COLUMN billing_key_requested_at datetime(6) NULL;
