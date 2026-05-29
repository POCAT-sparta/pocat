DROP PROCEDURE IF EXISTS add_likes_index;
CREATE PROCEDURE add_likes_index()
BEGIN
    IF EXISTS (
        SELECT 1 FROM information_schema.TABLES
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'likes'
    ) AND NOT EXISTS (
        SELECT 1 FROM information_schema.STATISTICS
        WHERE TABLE_SCHEMA = DATABASE()
          AND TABLE_NAME = 'likes'
          AND INDEX_NAME = 'idx_likes_user_auction'
    ) THEN
        CREATE INDEX idx_likes_user_auction ON likes (user_id, auction_id);
    END IF;
END;
CALL add_likes_index();
DROP PROCEDURE IF EXISTS add_likes_index;

DROP PROCEDURE IF EXISTS add_chats_constraint;
CREATE PROCEDURE add_chats_constraint()
BEGIN
    IF EXISTS (
        SELECT 1 FROM information_schema.TABLES
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'chats'
    ) AND NOT EXISTS (
        SELECT 1 FROM information_schema.TABLE_CONSTRAINTS
        WHERE TABLE_SCHEMA = DATABASE()
          AND TABLE_NAME = 'chats'
          AND CONSTRAINT_NAME = 'uk_chats_post_guest'
    ) THEN
        ALTER TABLE chats
            ADD CONSTRAINT uk_chats_post_guest UNIQUE (post_id, guest_id);
    END IF;
END;
CALL add_chats_constraint();
DROP PROCEDURE IF EXISTS add_chats_constraint;
