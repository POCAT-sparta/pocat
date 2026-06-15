-- These values were generated with LocalDateTime.now(Asia/Seoul), while the
-- production JVM, MySQL session, and the rest of the application use UTC.

UPDATE auctions
SET started_at = DATE_SUB(started_at, INTERVAL 9 HOUR),
    ended_at = DATE_SUB(ended_at, INTERVAL 9 HOUR),
    inspected_at = DATE_SUB(inspected_at, INTERVAL 9 HOUR);
