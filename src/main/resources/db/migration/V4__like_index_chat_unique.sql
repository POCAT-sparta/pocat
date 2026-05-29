CREATE INDEX IF NOT EXISTS idx_likes_user_auction
    ON likes (user_id, auction_id);

ALTER TABLE chats
    ADD CONSTRAINT IF NOT EXISTS uk_chats_post_guest
        UNIQUE (post_id, guest_id);
