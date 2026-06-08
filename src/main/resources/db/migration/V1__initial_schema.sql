-- V1: Full initial schema — all tables in their final state.
-- On a fresh DB, Flyway runs this before JPA starts, so every table V2+ references already exists.
-- All statements use CREATE TABLE IF NOT EXISTS so this is safe to run against an existing DB
-- (though flyway_schema_history checksum will differ — run `flyway repair` on existing DBs).

SET FOREIGN_KEY_CHECKS = 0;

-- ── 독립 테이블 ─────────────────────────────────────────────────────────────────

CREATE TABLE IF NOT EXISTS `series` (
    `id`         BIGINT       NOT NULL AUTO_INCREMENT,
    `name`       VARCHAR(100) NOT NULL,
    `name_ko`    VARCHAR(500) DEFAULT NULL,
    `created_at` DATETIME(6)  NOT NULL,
    `updated_at` DATETIME(6)  NOT NULL,
    `deleted_at` DATETIME(6)  DEFAULT NULL,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_series_name` (`name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `pokemon` (
    `id`         BIGINT       NOT NULL AUTO_INCREMENT,
    `name`       VARCHAR(100) NOT NULL,
    `name_ko`    VARCHAR(100) DEFAULT NULL,
    `created_at` DATETIME(6)  NOT NULL,
    `updated_at` DATETIME(6)  NOT NULL,
    `deleted_at` DATETIME(6)  DEFAULT NULL,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_pokemon_name` (`name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `users` (
    `id`            BIGINT       NOT NULL AUTO_INCREMENT,
    `email`         VARCHAR(255) NOT NULL,
    `password`      VARCHAR(255) NOT NULL,
    `nickname`      VARCHAR(100) NOT NULL,
    `phone`         VARCHAR(20)  DEFAULT NULL,
    `address`       VARCHAR(255) DEFAULT NULL,
    `billing_key`   VARCHAR(255) DEFAULT NULL,
    `user_role`     ENUM('ADMIN','USER') NOT NULL,
    `is_bid_blocked` BIT(1)      NOT NULL,
    `unpaid_strike` INT          NOT NULL,
    `created_at`    DATETIME(6)  DEFAULT NULL,
    `updated_at`    DATETIME(6)  DEFAULT NULL,
    `deleted_at`    DATETIME(6)  DEFAULT NULL,
    PRIMARY KEY (`id`),
    UNIQUE KEY `UK6dotkott2kjsp8vw4d0m25fb7` (`email`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `ai_prompt_template` (
    `id`          BIGINT       NOT NULL AUTO_INCREMENT,
    `card_grade`  VARCHAR(20)  NOT NULL,
    `prompt_text` TEXT         NOT NULL,
    `version`     INT          NOT NULL DEFAULT 1,
    `is_active`   TINYINT(1)   NOT NULL DEFAULT 1,
    `created_at`  DATETIME(6)  NOT NULL,
    `updated_at`  DATETIME(6)  NOT NULL,
    `deleted_at`  DATETIME(6)  DEFAULT NULL,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_prompt_grade` (`card_grade`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `outbox_events` (
    `id`            BIGINT       NOT NULL AUTO_INCREMENT,
    `topic`         VARCHAR(50)  NOT NULL,
    `partition_key` VARCHAR(100) NOT NULL,
    `event_type`    VARCHAR(100) NOT NULL,
    `payload`       TEXT         NOT NULL,
    `status`        VARCHAR(20)  NOT NULL,
    `retry_count`   INT          NOT NULL DEFAULT 0,
    `created_at`    DATETIME(6)  NOT NULL,
    `processed_at`  DATETIME(6)  DEFAULT NULL,
    PRIMARY KEY (`id`),
    KEY `idx_outbox_status_created` (`status`, `created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `webhook_events` (
    `id`         BIGINT      NOT NULL AUTO_INCREMENT,
    `event_type` VARCHAR(50) NOT NULL,
    `payment_id` VARCHAR(100) NOT NULL,
    `raw_body`   TEXT        NOT NULL,
    `status`     ENUM('FAILED','PROCESSED','RECEIVED') NOT NULL,
    `created_at` DATETIME(6) DEFAULT NULL,
    PRIMARY KEY (`id`),
    UNIQUE KEY `UKslmo429fqscq0l8jxt8o7r74a` (`payment_id`, `event_type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ── series 의존 ─────────────────────────────────────────────────────────────────

CREATE TABLE IF NOT EXISTS `pokemon_sets` (
    `id`         BIGINT       NOT NULL AUTO_INCREMENT,
    `series_id`  BIGINT       DEFAULT NULL,
    `set_id`     VARCHAR(50)  NOT NULL,
    `name`       VARCHAR(100) NOT NULL,
    `name_ko`    VARCHAR(1000) DEFAULT NULL,
    `created_at` DATETIME(6)  NOT NULL,
    `updated_at` DATETIME(6)  NOT NULL,
    `deleted_at` DATETIME(6)  DEFAULT NULL,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_pokemon_sets_set_id` (`set_id`),
    KEY `fk_pokemon_sets_series` (`series_id`),
    CONSTRAINT `fk_pokemon_sets_series` FOREIGN KEY (`series_id`) REFERENCES `series` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ── series, pokemon_sets, pokemon 의존 ──────────────────────────────────────────

CREATE TABLE IF NOT EXISTS `cards` (
    `id`            BIGINT       NOT NULL AUTO_INCREMENT,
    `user_id`       BIGINT       NOT NULL,
    `tcgdex_id`     VARCHAR(100) DEFAULT NULL,
    `name`          VARCHAR(255) NOT NULL,
    `card_number`   VARCHAR(20)  NOT NULL,
    `rarity`        VARCHAR(50)  NOT NULL,
    `image_url`     VARCHAR(500) DEFAULT NULL,
    `reject_reason` TEXT         DEFAULT NULL,
    `category`      ENUM('ENERGY','POKEMON','TRAINERS','UNKNOWN') NOT NULL,
    `grade`         ENUM('BGS_10','PSA_10','PSA_9') NOT NULL,
    `source`        ENUM('MANUAL','TCGDEX') NOT NULL,
    `status`        ENUM('ACTIVE','PENDING','REJECTED') NOT NULL,
    `series_id`     BIGINT       DEFAULT NULL,
    `pokemon_set_id` BIGINT      DEFAULT NULL,
    `pokemon_id`    BIGINT       DEFAULT NULL,
    `created_at`    DATETIME(6)  DEFAULT NULL,
    `updated_at`    DATETIME(6)  DEFAULT NULL,
    `deleted_at`    DATETIME(6)  DEFAULT NULL,
    PRIMARY KEY (`id`),
    UNIQUE KEY `UKigthbk12f2bio93w9cosk48r8` (`tcgdex_id`),
    KEY `idx_cards_status` (`status`),
    KEY `idx_cards_user_id` (`user_id`),
    KEY `idx_cards_user_id_status` (`user_id`, `status`),
    KEY `idx_cards_grade` (`grade`),
    KEY `idx_cards_category` (`category`),
    KEY `idx_cards_created_at` (`created_at`),
    KEY `fk_cards_series` (`series_id`),
    KEY `fk_cards_pokemon_set` (`pokemon_set_id`),
    KEY `fk_cards_pokemon` (`pokemon_id`),
    CONSTRAINT `fk_cards_series`      FOREIGN KEY (`series_id`)      REFERENCES `series` (`id`),
    CONSTRAINT `fk_cards_pokemon_set` FOREIGN KEY (`pokemon_set_id`) REFERENCES `pokemon_sets` (`id`),
    CONSTRAINT `fk_cards_pokemon`     FOREIGN KEY (`pokemon_id`)     REFERENCES `pokemon` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ── users 의존 ──────────────────────────────────────────────────────────────────

CREATE TABLE IF NOT EXISTS `notifications` (
    `id`           BIGINT       NOT NULL AUTO_INCREMENT,
    `user_id`      BIGINT       NOT NULL,
    `type`         ENUM('AUCTION_ACTIVATED','AUCTION_CANCELLED','AUCTION_LOST','AUCTION_SOLD',
                        'AUCTION_WON','AUTO_PAYMENT_FAILED','BID_CREATED','BID_OUTBID',
                        'DIRECT_PAYMENT_FAILED','ESCALATED_PAYMENT_OPPORTUNITY','INSPECTION_FAILED',
                        'INSPECTION_PASSED','ORDER_CANCELLED','PAYMENT_COMPLETED','PAYMENT_FINAL_FAILED',
                        'REFUND_APPROVED','REFUND_REJECTED','REFUND_REQUESTED','SETTLEMENT_COMPLETED',
                        'SETTLEMENT_CREATED','SHIPPING','SHIPPING_COMPLETED') NOT NULL,
    `message`      VARCHAR(255) NOT NULL,
    `related_data` VARCHAR(255) DEFAULT NULL,
    `is_read`      BIT(1)       NOT NULL,
    `created_at`   DATETIME(6)  DEFAULT NULL,
    `updated_at`   DATETIME(6)  DEFAULT NULL,
    `deleted_at`   DATETIME(6)  DEFAULT NULL,
    PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `free_posts` (
    `id`            BIGINT       NOT NULL AUTO_INCREMENT,
    `user_id`       BIGINT       NOT NULL,
    `title`         VARCHAR(255) NOT NULL,
    `content`       TEXT         NOT NULL,
    `view_count`    INT          NOT NULL,
    `comment_count` INT          NOT NULL,
    `created_at`    DATETIME(6)  DEFAULT NULL,
    `updated_at`    DATETIME(6)  DEFAULT NULL,
    `deleted_at`    DATETIME(6)  DEFAULT NULL,
    PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `trade_posts` (
    `id`         BIGINT       NOT NULL AUTO_INCREMENT,
    `user_id`    BIGINT       NOT NULL,
    `title`      VARCHAR(255) NOT NULL,
    `content`    TEXT         NOT NULL,
    `thumbnail`  TEXT         DEFAULT NULL,
    `price`      BIGINT       NOT NULL,
    `view_count` INT          NOT NULL,
    `created_at` DATETIME(6)  DEFAULT NULL,
    `updated_at` DATETIME(6)  DEFAULT NULL,
    `deleted_at` DATETIME(6)  DEFAULT NULL,
    PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `ai_chat_sessions` (
    `id`             BIGINT      NOT NULL AUTO_INCREMENT,
    `user_id`        BIGINT      NOT NULL,
    `session_uuid`   VARCHAR(36) NOT NULL,
    `total_tokens`   INT         NOT NULL DEFAULT 0,
    `is_expired`     TINYINT(1)  NOT NULL DEFAULT 0,
    `last_active_at` DATETIME(6) NOT NULL,
    `created_at`     DATETIME(6) NOT NULL,
    `updated_at`     DATETIME(6) NOT NULL,
    `deleted_at`     DATETIME(6) DEFAULT NULL,
    PRIMARY KEY (`id`),
    UNIQUE KEY `session_uuid` (`session_uuid`),
    KEY `idx_ai_chat_session_user_id` (`user_id`),
    KEY `idx_ai_chat_session_last_active` (`last_active_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ── cards 의존 ──────────────────────────────────────────────────────────────────

CREATE TABLE IF NOT EXISTS `auctions` (
    `id`                BIGINT       NOT NULL AUTO_INCREMENT,
    `seller_id`         BIGINT       NOT NULL,
    `card_id`           BIGINT       NOT NULL,
    `title`             VARCHAR(255) NOT NULL,
    `description`       TEXT         DEFAULT NULL,
    `status`            ENUM('ACTIVE','APPROVED','CANCELLED','ENDED','INSPECTING','NO_BIDDER',
                             'PAYMENT_PENDING','PENDING','REJECTED') NOT NULL,
    `starting_price`    BIGINT       NOT NULL,
    `buyout_price`      BIGINT       DEFAULT NULL,
    `highest_price`     BIGINT       DEFAULT NULL,
    `highest_bidder_id` BIGINT       DEFAULT NULL,
    `started_at`        DATETIME(6)  DEFAULT NULL,
    `ended_at`          DATETIME(6)  DEFAULT NULL,
    `inspected_at`      DATETIME(6)  DEFAULT NULL,
    `inspected_by`      BIGINT       DEFAULT NULL,
    `reason`            TEXT         DEFAULT NULL,
    `created_at`        DATETIME(6)  DEFAULT NULL,
    `updated_at`        DATETIME(6)  DEFAULT NULL,
    `deleted_at`        DATETIME(6)  DEFAULT NULL,
    PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `card_ai_analysis` (
    `id`                 BIGINT      NOT NULL AUTO_INCREMENT,
    `card_id`            BIGINT      NOT NULL,
    `price_trend`        VARCHAR(10) NOT NULL,
    `fair_value_estimate` BIGINT     DEFAULT NULL,
    `demand_level`       VARCHAR(10) NOT NULL,
    `summary`            TEXT        DEFAULT NULL,
    `highlights`         TEXT        DEFAULT NULL,
    `risk_factors`       TEXT        DEFAULT NULL,
    `keywords`           TEXT        DEFAULT NULL,
    `analysis_model`     VARCHAR(100) DEFAULT NULL,
    `prompt_tokens`      INT         DEFAULT NULL,
    `completion_tokens`  INT         DEFAULT NULL,
    `created_at`         DATETIME(6) NOT NULL,
    `updated_at`         DATETIME(6) NOT NULL,
    `deleted_at`         DATETIME(6) DEFAULT NULL,
    PRIMARY KEY (`id`),
    KEY `idx_card_ai_analysis_card_id` (`card_id`),
    CONSTRAINT `fk_card_ai_analysis_card` FOREIGN KEY (`card_id`) REFERENCES `cards` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ── auctions 의존 ───────────────────────────────────────────────────────────────

CREATE TABLE IF NOT EXISTS `auction_bids` (
    `id`         BIGINT NOT NULL AUTO_INCREMENT,
    `auction_id` BIGINT NOT NULL,
    `user_id`    BIGINT NOT NULL,
    `bid_price`  BIGINT NOT NULL,
    `status`     ENUM('CANCELLED','LEADING','LOST','OUTBID','WON') NOT NULL,
    `created_at` DATETIME(6) DEFAULT NULL,
    `updated_at` DATETIME(6) DEFAULT NULL,
    `deleted_at` DATETIME(6) DEFAULT NULL,
    PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `auction_snapshots` (
    `id`            BIGINT NOT NULL AUTO_INCREMENT,
    `auction_id`    BIGINT NOT NULL,
    `final_price`   BIGINT DEFAULT NULL,
    `snapshot_json` TEXT   DEFAULT NULL,
    `created_at`    DATETIME(6) DEFAULT NULL,
    `updated_at`    DATETIME(6) DEFAULT NULL,
    `deleted_at`    DATETIME(6) DEFAULT NULL,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_auction_snapshots_auction_id` (`auction_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `likes` (
    `id`         BIGINT NOT NULL AUTO_INCREMENT,
    `user_id`    BIGINT NOT NULL,
    `auction_id` BIGINT DEFAULT NULL,
    `created_at` DATETIME(6) DEFAULT NULL,
    `updated_at` DATETIME(6) DEFAULT NULL,
    `deleted_at` DATETIME(6) DEFAULT NULL,
    PRIMARY KEY (`id`),
    KEY `idx_likes_user_auction` (`user_id`, `auction_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `orders` (
    `id`               BIGINT      NOT NULL AUTO_INCREMENT,
    `order_uid`        VARCHAR(50) NOT NULL,
    `buyer_id`         BIGINT      NOT NULL,
    `seller_id`        BIGINT      DEFAULT NULL,
    `card_id`          BIGINT      NOT NULL,
    `auction_id`       BIGINT      DEFAULT NULL,
    `bidder_rank`      INT         DEFAULT NULL,
    `order_type`       ENUM('AUCTION','BUYOUT') NOT NULL,
    `final_price`      BIGINT      NOT NULL,
    `status`           ENUM('AUTO_PAYMENT_FAILED','CANCELLED','DIRECT_PAYMENT_FAILED',
                            'ORDER_COMPLETED','PAYMENT_COMPLETED','PAYMENT_PENDING',
                            'REFUNDED','SHIPPING','SHIPPING_COMPLETED') NOT NULL,
    `delivery_status`  ENUM('CANCELLED','COMPLETED','PREPARING','SHIPPING') DEFAULT NULL,
    `cancel_reason`    VARCHAR(255) DEFAULT NULL,
    `payment_deadline` DATETIME(6)  DEFAULT NULL,
    `created_at`       DATETIME(6)  DEFAULT NULL,
    `updated_at`       DATETIME(6)  DEFAULT NULL,
    `deleted_at`       DATETIME(6)  DEFAULT NULL,
    PRIMARY KEY (`id`),
    UNIQUE KEY `UKi0poo21rsght60tvcy7qfw59p` (`order_uid`),
    UNIQUE KEY `uk_auction_id_bidder_rank` (`auction_id`, `bidder_rank`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ── free_posts 의존 ─────────────────────────────────────────────────────────────

CREATE TABLE IF NOT EXISTS `comments` (
    `id`          BIGINT NOT NULL AUTO_INCREMENT,
    `free_post_id` BIGINT NOT NULL,
    `user_id`     BIGINT NOT NULL,
    `parent_id`   BIGINT DEFAULT NULL,
    `content`     TEXT   NOT NULL,
    `created_at`  DATETIME(6) DEFAULT NULL,
    `updated_at`  DATETIME(6) DEFAULT NULL,
    `deleted_at`  DATETIME(6) DEFAULT NULL,
    PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ── trade_posts 의존 ────────────────────────────────────────────────────────────

CREATE TABLE IF NOT EXISTS `chats` (
    `id`         BIGINT NOT NULL AUTO_INCREMENT,
    `post_id`    BIGINT NOT NULL,
    `owner_id`   BIGINT NOT NULL,
    `guest_id`   BIGINT NOT NULL,
    `owner_left` BIT(1) NOT NULL,
    `guest_left` BIT(1) NOT NULL,
    `status`     ENUM('ACTIVE','CANCELLED','COMPLETED') NOT NULL,
    `created_at` DATETIME(6) DEFAULT NULL,
    `updated_at` DATETIME(6) DEFAULT NULL,
    `deleted_at` DATETIME(6) DEFAULT NULL,
    PRIMARY KEY (`id`),
    UNIQUE KEY `UK49oew9dktwaeim0n3jxkawwk2` (`post_id`, `guest_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ── chats 의존 ──────────────────────────────────────────────────────────────────

CREATE TABLE IF NOT EXISTS `chat_messages` (
    `id`         BIGINT NOT NULL AUTO_INCREMENT,
    `chat_id`    BIGINT NOT NULL,
    `sender_id`  BIGINT NOT NULL,
    `message`    TEXT   NOT NULL,
    `is_read`    BIT(1) NOT NULL,
    `created_at` DATETIME(6) DEFAULT NULL,
    `updated_at` DATETIME(6) DEFAULT NULL,
    `deleted_at` DATETIME(6) DEFAULT NULL,
    PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ── orders 의존 ─────────────────────────────────────────────────────────────────

CREATE TABLE IF NOT EXISTS `order_snapshots` (
    `id`             BIGINT      NOT NULL AUTO_INCREMENT,
    `order_uid`      VARCHAR(50) NOT NULL,
    `final_price`    BIGINT      NOT NULL,
    `fee`            BIGINT      NOT NULL,
    `fee_rate`       BIGINT      NOT NULL,
    `seller_amount`  BIGINT      NOT NULL,
    `snapshot_json`  TEXT        DEFAULT NULL,
    `created_at`     DATETIME(6) DEFAULT NULL,
    `updated_at`     DATETIME(6) DEFAULT NULL,
    `deleted_at`     DATETIME(6) DEFAULT NULL,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_order_snapshots_order_uid` (`order_uid`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `payments` (
    `id`                       BIGINT      NOT NULL AUTO_INCREMENT,
    `order_id`                 BIGINT      NOT NULL,
    `payment_uid`              VARCHAR(50) NOT NULL,
    `payment_type`             ENUM('BILLING_KEY','PG_DIRECT') NOT NULL,
    `payment_method`           VARCHAR(50) DEFAULT NULL,
    `amount`                   BIGINT      NOT NULL,
    `status`                   ENUM('CANCELLED','CANCEL_HTTP_ERROR','COMPLETED','FAILED','PENDING','REFUNDED') NOT NULL,
    `paid_at`                  DATETIME(6) DEFAULT NULL,
    `billing_key_requested_at` DATETIME(6) DEFAULT NULL,
    `created_at`               DATETIME(6) DEFAULT NULL,
    `updated_at`               DATETIME(6) DEFAULT NULL,
    `deleted_at`               DATETIME(6) DEFAULT NULL,
    PRIMARY KEY (`id`),
    UNIQUE KEY `UKll5wr1fe73lohconkmmup7mwq` (`payment_uid`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `settlements` (
    `id`             BIGINT      NOT NULL AUTO_INCREMENT,
    `order_id`       BIGINT      NOT NULL,
    `settlement_uid` VARCHAR(50) NOT NULL,
    `seller_id`      BIGINT      DEFAULT NULL,
    `total_price`    BIGINT      NOT NULL,
    `platform_fee`   BIGINT      NOT NULL,
    `seller_amount`  BIGINT      NOT NULL,
    `status`         ENUM('COMPLETED','PENDING','REFUNDED') NOT NULL,
    `settled_at`     DATETIME(6) DEFAULT NULL,
    `created_at`     DATETIME(6) DEFAULT NULL,
    `updated_at`     DATETIME(6) DEFAULT NULL,
    `deleted_at`     DATETIME(6) DEFAULT NULL,
    PRIMARY KEY (`id`),
    UNIQUE KEY `UK139014vvhp0mo9ecrtxtsjcql` (`order_id`),
    UNIQUE KEY `UKrclsmsls0ys9fncguw76fwp7x` (`settlement_uid`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ── payments 의존 ───────────────────────────────────────────────────────────────

CREATE TABLE IF NOT EXISTS `refunds` (
    `id`             BIGINT       NOT NULL AUTO_INCREMENT,
    `order_id`       BIGINT       NOT NULL,
    `payment_id`     BIGINT       NOT NULL,
    `amount`         BIGINT       NOT NULL,
    `reason`         VARCHAR(100) NOT NULL,
    `reject_reason`  VARCHAR(100) DEFAULT NULL,
    `failure_reason` VARCHAR(255) DEFAULT NULL,
    `status`         ENUM('COMPLETED','FAILED_FINAL','FAILED_RETRYABLE','PROCESSING','REJECTED','REQUESTED') NOT NULL,
    `retry_count`    INT          NOT NULL,
    `next_retry_at`  DATETIME(6)  DEFAULT NULL,
    `created_at`     DATETIME(6)  DEFAULT NULL,
    `updated_at`     DATETIME(6)  DEFAULT NULL,
    `deleted_at`     DATETIME(6)  DEFAULT NULL,
    PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ── ai_chat_sessions 의존 ───────────────────────────────────────────────────────

CREATE TABLE IF NOT EXISTS `ai_chat_messages` (
    `id`                 BIGINT NOT NULL AUTO_INCREMENT,
    `ai_chat_session_id` BIGINT NOT NULL,
    `role`               VARCHAR(20) NOT NULL,
    `content`            LONGTEXT    NOT NULL,
    `token_count`        INT         NOT NULL DEFAULT 0,
    `created_at`         DATETIME(6) NOT NULL,
    `updated_at`         DATETIME(6) NOT NULL,
    `deleted_at`         DATETIME(6) DEFAULT NULL,
    PRIMARY KEY (`id`),
    KEY `idx_ai_chat_message_session_id` (`ai_chat_session_id`),
    KEY `idx_ai_chat_message_created_at` (`created_at`),
    CONSTRAINT `fk_ai_chat_messages_session` FOREIGN KEY (`ai_chat_session_id`) REFERENCES `ai_chat_sessions` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

SET FOREIGN_KEY_CHECKS = 1;
