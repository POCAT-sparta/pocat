# Domain Separation: card → card / series / set / pokemon

**Date**: 2026-05-27  
**Status**: Approved  
**Scope**: 기존 `domain/card` 단일 도메인을 `card`, `series`, `set`, `pokemon` 4개 도메인으로 분리

---

## 1. 배경 및 목표

현재 `domain/card`에 Series/Set/Pokemon 관련 코드가 모두 혼재되어 있음.

- `Card` 엔티티가 `series`, `setId`, `setName`을 plain String으로 보유 → 정규화 없음
- `SeriesNameDictionary`, `SetNameDictionary`, `PokemonNameDictionary` 세 YAML 기반 유틸이 card/util/ 에 위치
- Series/Set/Pokemon 데이터를 독립적으로 관리(CRUD)하거나 조회할 수 없음

**목표**
- Series, PokemonSet, Pokemon을 독립 JPA 엔티티 및 도메인으로 분리
- Card는 세 도메인을 FK로 참조
- 각 도메인에 Admin CRUD API 제공
- 한글 이름(nameKo)을 DB에서 관리 → YAML 딕셔너리 의존 제거
- ES 검색 동작(keyword 파라미터, 한/영 모두)은 기존과 동일하게 유지

---

## 2. 패키지 구조

```
domain/
├── card/               기존 — Card 엔티티 FK 교체, util/ 제거
│   ├── entity/Card.java
│   ├── document/CardDocument.java   (ES, 구조 변경 없음)
│   ├── service/CardCommandService.java
│   ├── service/CardQueryService.java
│   ├── service/CardEsMigrationService.java
│   └── ...
├── series/             신규
│   ├── entity/Series.java
│   ├── repository/SeriesRepository.java
│   ├── service/SeriesCommandService.java
│   ├── service/SeriesQueryService.java
│   └── controller/AdminSeriesController.java
├── set/                신규 (확장팩)
│   ├── entity/PokemonSet.java      (Java Set 충돌 방지)
│   ├── repository/PokemonSetRepository.java
│   ├── service/PokemonSetCommandService.java
│   ├── service/PokemonSetQueryService.java
│   └── controller/AdminPokemonSetController.java
└── pokemon/            신규
    ├── entity/Pokemon.java
    ├── repository/PokemonRepository.java
    ├── service/PokemonCommandService.java
    ├── service/PokemonQueryService.java
    └── controller/AdminPokemonController.java
```

삭제 대상:
- `domain/card/util/SeriesNameDictionary.java`
- `domain/card/util/SetNameDictionary.java`
- `domain/card/util/PokemonNameDictionary.java`

---

## 3. 엔티티 설계

### Series

```java
@Entity
@Table(name = "series",
    uniqueConstraints = @UniqueConstraint(columnNames = "name"))
public class Series extends BaseEntity {
    Long id;
    String name;      // "Sword & Shield" (TCGdex 영문명, unique)
    String nameKo;    // "검과 방패" (nullable — admin API 또는 시더로 채움)
}
```

### PokemonSet

```java
@Entity
@Table(name = "pokemon_sets",
    uniqueConstraints = @UniqueConstraint(columnNames = "set_id"))
public class PokemonSet extends BaseEntity {
    Long id;
    Long seriesId;    // FK → series.id (nullable)
    String setId;     // "swsh5" (TCGdex ID, unique)
    String name;      // "Rebel Clash"
    String nameKo;    // "반역 크래시" (nullable)
}
```

### Pokemon

```java
@Entity
@Table(name = "pokemon",
    uniqueConstraints = @UniqueConstraint(columnNames = "name"))
public class Pokemon extends BaseEntity {
    Long id;
    String name;      // "Charizard" (영문 포켓몬명, unique)
    String nameKo;    // "리자몽" (nullable)
}
```

### Card (변경)

| 컬럼 | 변경 내용 |
|------|-----------|
| `series` VARCHAR | ❌ 제거 |
| `set_id` VARCHAR | ❌ 제거 |
| `set_name` VARCHAR | ❌ 제거 |
| `series_id` BIGINT | ➕ 추가 (FK → series.id) |
| `pokemon_set_id` BIGINT | ➕ 추가 (FK → pokemon_sets.id) |
| `pokemon_id` BIGINT | ➕ 추가, nullable (FK → pokemon.id, Trainer/Energy는 null) |
| `name` VARCHAR | ✅ 유지 (전체 카드명 "Charizard ex") |

---

## 4. 데이터 흐름

### 4-1. 카드 등록 / TCGdex 동기화 시 자동 find-or-create

`CardCommandService` 및 `CardSyncService`에서 Card 저장 전 실행:

```
1. Series.findByName(card.series)      없으면 INSERT (nameKo=null)
2. PokemonSet.findBySetId(card.setId)  없으면 INSERT (seriesId 연결, nameKo=null)
3. category == POKEMON 인 경우:
     슬라이딩 윈도우로 포켓몬명 추출 (기존 PokemonNameDictionary 로직)
     Pokemon.findByName(pokemonName)   없으면 INSERT (nameKo=null)
4. Card 저장 (seriesId, pokemonSetId, pokemonId FK 세팅)
```

nameKo는 처음엔 null → 어드민 API로 수동 입력하거나 시더가 채움.

### 4-2. YAML 시더 (최초 1회, ApplicationReadyEvent)

```
pokemon-names.yml → Pokemon 테이블 INSERT
  (name=영문, nameKo=한글, 이미 존재하면 스킵)

기존 cards 순회 → 카드명에서 포켓몬명 추출 → pokemon_id UPDATE
  (POKEMON category이고 pokemon_id IS NULL인 카드만)
```

series/set 시드 데이터는 Flyway V2에서 처리 (아래 섹션 참조).

### 4-3. Admin CRUD API

```
POST   /api/v1/admin/series
PATCH  /api/v1/admin/series/{id}     nameKo 수정 등
DELETE /api/v1/admin/series/{id}

POST   /api/v1/admin/sets
PATCH  /api/v1/admin/sets/{id}
DELETE /api/v1/admin/sets/{id}

POST   /api/v1/admin/pokemon
PATCH  /api/v1/admin/pokemon/{id}    nameKo 수정
DELETE /api/v1/admin/pokemon/{id}
```

---

## 5. DB 마이그레이션 전략

Flyway가 JPA보다 먼저 실행되므로 Flyway 스크립트가 새 테이블 생성부터 컬럼 교체까지 전담.

### Flyway V2__domain_separation.sql

```
① CREATE TABLE series (id, name UNIQUE, name_ko, created_at, updated_at)
② CREATE TABLE pokemon_sets (id, series_id FK, set_id UNIQUE, name, name_ko, ...)
③ series/set 시드 데이터 INSERT (series-names.yml, set-names.yml 기반 하드코딩)
④ ALTER TABLE cards
     ADD COLUMN series_id BIGINT,
     ADD COLUMN pokemon_set_id BIGINT,
     ADD COLUMN pokemon_id BIGINT
⑤ UPDATE cards: series_id = (SELECT id FROM series WHERE name = cards.series)
⑥ UPDATE cards: pokemon_set_id = (SELECT id FROM pokemon_sets WHERE set_id = cards.set_id)
⑦ ALTER TABLE cards
     ADD CONSTRAINT fk_cards_series      FOREIGN KEY (series_id) REFERENCES series(id),
     ADD CONSTRAINT fk_cards_pokemon_set FOREIGN KEY (pokemon_set_id) REFERENCES pokemon_sets(id),
     ADD CONSTRAINT fk_cards_pokemon     FOREIGN KEY (pokemon_id) REFERENCES pokemon(id)
⑧ ALTER TABLE cards DROP COLUMN series, DROP COLUMN set_id, DROP COLUMN set_name
```

> **주의**: 기존 cards 데이터 중 series 값이 시드 데이터에 없는 경우 series_id가 NULL로 남을 수 있음.  
> Flyway ⑤ UPDATE 후 `SELECT COUNT(*) FROM cards WHERE series_id IS NULL` 으로 확인 권장.  
> 누락 데이터는 Admin API로 Series/PokemonSet 추가 후 수동 UPDATE 처리.

### ApplicationReadyEvent 시더 (Java)

pokemon_id 매핑은 슬라이딩 윈도우 알고리즘이 필요하므로 Java로 처리.

```
Pokemon 테이블이 비어있으면:
  pokemon-names.yml 로드 → Pokemon INSERT

cards.pokemon_id IS NULL인 POKEMON 카드에 대해:
  기존 PokemonNameDictionary 슬라이딩 윈도우 로직으로 포켓몬명 추출
  Pokemon 조회 → pokemon_id UPDATE
```

| 담당 | 역할 |
|------|------|
| Flyway V2 | 테이블 생성, series/set 시드, FK 컬럼 추가/이관, 구 컬럼 DROP |
| Java 시더 | pokemon 시드 + cards.pokemon_id 채우기 |
| JPA ddl-auto:update | 엔티티 변경사항 보조 |

---

## 6. ES 영향

`CardDocument` 구조 변경 없음. 6개 검색 필드는 그대로 유지되며 데이터 공급처만 변경.

| CardDocument 필드 | 변경 전 | 변경 후 |
|---|---|---|
| `name` | Card.name | Card.name (동일) |
| `nameKo` | PokemonNameDictionary | Pokemon.nameKo |
| `series.text` | Card.series | Series.name |
| `seriesKo` | SeriesNameDictionary | Series.nameKo |
| `setName.text` | Card.setName | PokemonSet.name |
| `setNameKo` | SetNameDictionary | PokemonSet.nameKo |

`CardQueryService`의 `cross_fields + AND` 멀티매치 쿼리 변경 없음.  
`keyword=리자몽`, `keyword=Charizard`, `keyword=반역크래시 리자몽` 등 기존 동작 유지.

### ES 필터 번역 (series/set 파라미터)

현재 `CardQueryService`에서 `SeriesNameDictionary.translate()`, `SetNameDictionary.translate()`로  
한글 입력 → 영문 ES term 필터 값으로 변환. 딕셔너리 삭제 후 대체:

- `SeriesQueryService.translate(input)`: Series 테이블에서 nameKo 또는 name으로 조회 → 영문명 반환
- `PokemonSetQueryService.translate(input)`: PokemonSet 테이블에서 동일 방식
- 매핑 없으면 입력값 그대로 반환 (영문 직접 입력 호환 유지)

---

## 7. 구현 순서

1. Flyway V2 SQL 작성 (series/set 테이블 + 데이터 이관)
2. Series, PokemonSet, Pokemon 엔티티 + 리포지토리 생성
3. Card 엔티티에서 string 컬럼 제거, FK 컬럼 추가
4. SeriesCommandService, PokemonSetCommandService (find-or-create 포함)
5. PokemonCommandService + ApplicationReadyEvent 시더
6. CardCommandService / CardSyncService — find-or-create 연결
7. CardQueryService — Dictionary 호출 → 엔티티 조회로 교체
8. CardEsMigrationService — Dictionary 호출 → 엔티티 조회로 교체
9. Admin Controller 3개 (Series, PokemonSet, Pokemon)
10. 기존 Dictionary 클래스 삭제
11. ES 인덱스 재생성 및 es-migrate 실행
