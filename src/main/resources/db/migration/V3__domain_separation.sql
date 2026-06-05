-- V3: series/pokemon_sets/pokemon 도메인 분리 — cards에서 FK 컬럼 추가 및 구 컬럼 제거.
-- V1이 최종 스키마를 포함하므로 모든 ALTER TABLE을 idempotent하게 처리.

-- ① 신규 테이블 생성 (V1에서 이미 생성됨, IF NOT EXISTS로 안전)
CREATE TABLE IF NOT EXISTS series (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    name        VARCHAR(100) NOT NULL,
    name_ko     VARCHAR(500),
    created_at  DATETIME(6) NOT NULL,
    updated_at  DATETIME(6) NOT NULL,
    deleted_at  DATETIME(6),
    UNIQUE KEY uk_series_name (name)
) DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS pokemon_sets (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    series_id   BIGINT,
    set_id      VARCHAR(50) NOT NULL,
    name        VARCHAR(100) NOT NULL,
    name_ko     VARCHAR(1000),
    created_at  DATETIME(6) NOT NULL,
    updated_at  DATETIME(6) NOT NULL,
    deleted_at  DATETIME(6),
    UNIQUE KEY uk_pokemon_sets_set_id (set_id),
    CONSTRAINT fk_pokemon_sets_series FOREIGN KEY (series_id) REFERENCES series(id)
) DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS pokemon (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    name        VARCHAR(100) NOT NULL,
    name_ko     VARCHAR(100),
    created_at  DATETIME(6) NOT NULL,
    updated_at  DATETIME(6) NOT NULL,
    deleted_at  DATETIME(6),
    UNIQUE KEY uk_pokemon_name (name)
) DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ② 기존 cards 데이터로 series 시드 (cards.series 컬럼이 존재할 때만 실행)
SET @seed_series = (
    SELECT IF(
        (SELECT COUNT(*) FROM information_schema.COLUMNS
         WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'cards' AND COLUMN_NAME = 'series') > 0,
        'INSERT IGNORE INTO series (name, name_ko, created_at, updated_at) SELECT DISTINCT series, NULL, NOW(6), NOW(6) FROM cards WHERE series IS NOT NULL AND series != \'\'',
        'SELECT 1'
    )
);
PREPARE stmt FROM @seed_series;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- ③ 기존 cards 데이터로 pokemon_sets 시드 (cards.set_id 컬럼이 존재할 때만 실행)
SET @seed_sets = (
    SELECT IF(
        (SELECT COUNT(*) FROM information_schema.COLUMNS
         WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'cards' AND COLUMN_NAME = 'set_id') > 0,
        'INSERT IGNORE INTO pokemon_sets (set_id, name, series_id, name_ko, created_at, updated_at) SELECT DISTINCT c.set_id, c.set_name, s.id, NULL, NOW(6), NOW(6) FROM cards c LEFT JOIN series s ON s.name = c.series WHERE c.set_id IS NOT NULL AND c.set_id != \'\'',
        'SELECT 1'
    )
);
PREPARE stmt FROM @seed_sets;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- ④ series_id 컬럼 추가 (없을 때만)
SET @add_series_id = (
    SELECT IF(
        (SELECT COUNT(*) FROM information_schema.COLUMNS
         WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'cards' AND COLUMN_NAME = 'series_id') > 0,
        'SELECT 1',
        'ALTER TABLE cards ADD COLUMN series_id BIGINT AFTER card_number'
    )
);
PREPARE stmt FROM @add_series_id;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- ⑤ pokemon_set_id 컬럼 추가 (없을 때만)
SET @add_pokemon_set_id = (
    SELECT IF(
        (SELECT COUNT(*) FROM information_schema.COLUMNS
         WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'cards' AND COLUMN_NAME = 'pokemon_set_id') > 0,
        'SELECT 1',
        'ALTER TABLE cards ADD COLUMN pokemon_set_id BIGINT AFTER series_id'
    )
);
PREPARE stmt FROM @add_pokemon_set_id;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- ⑥ pokemon_id 컬럼 추가 (없을 때만)
SET @add_pokemon_id = (
    SELECT IF(
        (SELECT COUNT(*) FROM information_schema.COLUMNS
         WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'cards' AND COLUMN_NAME = 'pokemon_id') > 0,
        'SELECT 1',
        'ALTER TABLE cards ADD COLUMN pokemon_id BIGINT AFTER pokemon_set_id'
    )
);
PREPARE stmt FROM @add_pokemon_id;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- ⑦ 기존 string 값으로 FK 컬럼 채우기 (series 컬럼이 아직 존재할 때만)
SET @fill_series_id = (
    SELECT IF(
        (SELECT COUNT(*) FROM information_schema.COLUMNS
         WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'cards' AND COLUMN_NAME = 'series') > 0,
        'UPDATE cards c JOIN series s ON s.name = c.series SET c.series_id = s.id',
        'SELECT 1'
    )
);
PREPARE stmt FROM @fill_series_id;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @fill_pokemon_set_id = (
    SELECT IF(
        (SELECT COUNT(*) FROM information_schema.COLUMNS
         WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'cards' AND COLUMN_NAME = 'set_id') > 0,
        'UPDATE cards c JOIN pokemon_sets ps ON ps.set_id = c.set_id SET c.pokemon_set_id = ps.id',
        'SELECT 1'
    )
);
PREPARE stmt FROM @fill_pokemon_set_id;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- ⑧ FK 제약 추가 (없을 때만)
SET @add_fk_series = (
    SELECT IF(
        (SELECT COUNT(*) FROM information_schema.TABLE_CONSTRAINTS
         WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'cards'
           AND CONSTRAINT_NAME = 'fk_cards_series') > 0,
        'SELECT 1',
        'ALTER TABLE cards ADD CONSTRAINT fk_cards_series FOREIGN KEY (series_id) REFERENCES series(id)'
    )
);
PREPARE stmt FROM @add_fk_series;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @add_fk_pokemon_set = (
    SELECT IF(
        (SELECT COUNT(*) FROM information_schema.TABLE_CONSTRAINTS
         WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'cards'
           AND CONSTRAINT_NAME = 'fk_cards_pokemon_set') > 0,
        'SELECT 1',
        'ALTER TABLE cards ADD CONSTRAINT fk_cards_pokemon_set FOREIGN KEY (pokemon_set_id) REFERENCES pokemon_sets(id)'
    )
);
PREPARE stmt FROM @add_fk_pokemon_set;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @add_fk_pokemon = (
    SELECT IF(
        (SELECT COUNT(*) FROM information_schema.TABLE_CONSTRAINTS
         WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'cards'
           AND CONSTRAINT_NAME = 'fk_cards_pokemon') > 0,
        'SELECT 1',
        'ALTER TABLE cards ADD CONSTRAINT fk_cards_pokemon FOREIGN KEY (pokemon_id) REFERENCES pokemon(id)'
    )
);
PREPARE stmt FROM @add_fk_pokemon;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- ⑨ 구 컬럼 삭제 (존재할 때만)
SET @drop_series = (
    SELECT IF(
        (SELECT COUNT(*) FROM information_schema.COLUMNS
         WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'cards' AND COLUMN_NAME = 'series') > 0,
        'ALTER TABLE cards DROP COLUMN series',
        'SELECT 1'
    )
);
PREPARE stmt FROM @drop_series;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @drop_set_id = (
    SELECT IF(
        (SELECT COUNT(*) FROM information_schema.COLUMNS
         WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'cards' AND COLUMN_NAME = 'set_id') > 0,
        'ALTER TABLE cards DROP COLUMN set_id',
        'SELECT 1'
    )
);
PREPARE stmt FROM @drop_set_id;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @drop_set_name = (
    SELECT IF(
        (SELECT COUNT(*) FROM information_schema.COLUMNS
         WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'cards' AND COLUMN_NAME = 'set_name') > 0,
        'ALTER TABLE cards DROP COLUMN set_name',
        'SELECT 1'
    )
);
PREPARE stmt FROM @drop_set_name;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
