-- ① 신규 테이블 생성
CREATE TABLE IF NOT EXISTS series (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    name        VARCHAR(100) NOT NULL,
    name_ko     VARCHAR(500),
    created_at  DATETIME(6) NOT NULL,
    updated_at  DATETIME(6) NOT NULL,
    deleted_at  DATETIME(6),
    UNIQUE KEY uk_series_name (name)
);

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
);

CREATE TABLE IF NOT EXISTS pokemon (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    name        VARCHAR(100) NOT NULL,
    name_ko     VARCHAR(100),
    created_at  DATETIME(6) NOT NULL,
    updated_at  DATETIME(6) NOT NULL,
    deleted_at  DATETIME(6),
    UNIQUE KEY uk_pokemon_name (name)
);

-- ② 기존 cards 데이터로 series 시드 (nameKo는 DomainDataSeeder가 채움)
INSERT IGNORE INTO series (name, name_ko, created_at, updated_at)
SELECT DISTINCT series, NULL, NOW(6), NOW(6)
FROM cards
WHERE series IS NOT NULL AND series != '';

-- ③ 기존 cards 데이터로 pokemon_sets 시드
INSERT IGNORE INTO pokemon_sets (set_id, name, series_id, name_ko, created_at, updated_at)
SELECT DISTINCT
    c.set_id,
    c.set_name,
    s.id,
    NULL,
    NOW(6),
    NOW(6)
FROM cards c
LEFT JOIN series s ON s.name = c.series
WHERE c.set_id IS NOT NULL AND c.set_id != '';

-- ④ cards 테이블에 FK 컬럼 추가
ALTER TABLE cards
    ADD COLUMN series_id      BIGINT AFTER set_name,
    ADD COLUMN pokemon_set_id BIGINT AFTER series_id,
    ADD COLUMN pokemon_id     BIGINT AFTER pokemon_set_id;

-- ⑤ 기존 string 값으로 FK 컬럼 채우기
UPDATE cards c
JOIN series s ON s.name = c.series
SET c.series_id = s.id;

UPDATE cards c
JOIN pokemon_sets ps ON ps.set_id = c.set_id
SET c.pokemon_set_id = ps.id;

-- ⑥ FK 제약 추가
ALTER TABLE cards
    ADD CONSTRAINT fk_cards_series       FOREIGN KEY (series_id)      REFERENCES series(id),
    ADD CONSTRAINT fk_cards_pokemon_set  FOREIGN KEY (pokemon_set_id) REFERENCES pokemon_sets(id),
    ADD CONSTRAINT fk_cards_pokemon      FOREIGN KEY (pokemon_id)     REFERENCES pokemon(id);

-- ⑦ 인덱스
CREATE INDEX idx_cards_series_id      ON cards(series_id);
CREATE INDEX idx_cards_pokemon_set_id ON cards(pokemon_set_id);

-- ⑧ 구 컬럼 삭제
ALTER TABLE cards
    DROP COLUMN series,
    DROP COLUMN set_id,
    DROP COLUMN set_name;
