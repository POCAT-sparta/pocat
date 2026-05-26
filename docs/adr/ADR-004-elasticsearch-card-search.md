# ADR-004: 카드 검색 엔진 Elasticsearch 도입 및 한글 부분 검색 지원

| 항목 | 내용 |
|------|------|
| **날짜** | 2026-05-26 |
| **상태** | Accepted |
| **결정자** | 개발팀 전체 |

---

## 맥락 (Context)

POCAT 카드 거래 플랫폼에서 사용자는 카드명·시리즈·등급·희귀도 등 다양한 조건으로 카드를 검색한다. 기존 카드 검색은 MySQL LIKE 쿼리 기반으로 구현되어 있었으며, 다음 한계가 있었다.

- **한글 검색 불가**: 카드 원본 데이터는 영어명(`Charizard`)으로 저장되어 있어 한글(`리자몽`, `리자`)로 검색하면 결과가 없음
- **부분 검색 품질 낮음**: LIKE `%keyword%` 방식은 인덱스를 사용하지 못해 전체 테이블 스캔이 발생하며, 형태소 기반 분석 없이 단순 문자열 포함 여부만 확인
- **필터 누락**: `rarity` 필터가 검색 조건에 빠져 있어 희귀도 기반 필터링이 불가능
- **정렬 품질 없음**: 검색어와 관련도가 높은 카드를 우선 노출하는 기준이 없고 `createdAt DESC` 고정
- **한글 시리즈·확장팩명 검색 불가**: 시리즈·확장팩명은 영문으로 저장되어 있어 "소드실드", "반역크래시" 같은 한글 입력으로 필터링 불가

23,000건 이상의 카드 데이터를 보유한 상황에서 검색 품질 개선이 필요하다고 판단하였다.

---

## 결정 (Decision)

### 1. 검색 엔진: MySQL LIKE → Elasticsearch (Option B 채택)

**대안 검토**

| 방식 | 설명 | 문제점 |
|------|------|--------|
| Option A | MySQL FULLTEXT 인덱스 + 한글 토크나이저(n-gram) | MySQL 8.0 ngram_token_size 전역 설정 변경 필요, 운영 DB에 직접 영향, 한글 형태소 분석 한계 |
| **Option B** (채택) | Elasticsearch 도입, Spring Data Elasticsearch 연동 | 초기 인프라 설정 비용 있음 |

**결정**: Elasticsearch 8.18.0 도입

- 카드 데이터는 쓰기보다 읽기(검색)가 압도적으로 많은 read-heavy 워크로드
- ngram tokenizer, multi-match, 관련도 점수(_score) 등 검색 특화 기능을 기본 제공
- MySQL은 원본 데이터 저장(Source of Truth)으로만 유지, ES는 검색 전용으로 역할 분리

### 2. DB-ES 역할 분리 설계

```
MySQL (Source of Truth)          Elasticsearch (검색 전용)
─────────────────────────        ──────────────────────────
카드 원본 데이터 저장              카드 검색 인덱스
카드 단건 조회                    키워드·필터 검색
관리자 승인/수정/삭제              검색 결과 반환
```

ES 장애 시 카드 단건 조회, 관리자 기능은 MySQL에서 계속 동작한다. 검색 기능만 영향을 받는다.

### 3. ES 인덱스 매핑 설계

**Text vs Keyword 타입 선택 기준**

- `Text`: 검색어를 토큰으로 분석해 부분 일치 가능 → 이름·한글 별칭 검색에 사용
- `Keyword`: 정확히 일치해야 하는 필터 조건 → 분류값(`series`, `grade`, `rarity`, `category`, `status`)에 사용

| 필드 | 타입 | analyzer (색인) | searchAnalyzer (검색) | 용도 |
|------|------|-----------------|----------------------|------|
| `name` | Text | standard | standard | 영어 카드명 검색 |
| `nameKo` | Text | ngram_analyzer | standard | 한글 포켓몬명 부분 검색 |
| `seriesKo` | Text | ngram_analyzer | standard | 한글 시리즈명 통합 키워드 검색 |
| `setNameKo` | Text | ngram_analyzer | standard | 한글 확장팩명 통합 키워드 검색 |
| `series` | Keyword | — | — | 정확 필터 |
| `setName` | Keyword | — | — | 정확 필터 |
| `grade` | Keyword | — | — | 정확 필터 |
| `rarity` | Keyword | — | — | 정확 필터 |
| `category` | Keyword | — | — | 정확 필터 |
| `status` | Keyword | — | — | ACTIVE 고정 필터 |

### 4. 한글 검색: ngram 분석기 도입

**대안 검토**

| 방식 | 설명 | 문제점 |
|------|------|--------|
| 동의어 사전 | `리자몽 → Charizard` 동의어 등록 | 포켓몬 1,025종 동의어 쌍을 ES 서버 파일로 관리해야 하며, 신규 포켓몬 추가 시 ES 재설정 필요 |
| **ngram + 한글 사전** (채택) | `nameKo` 필드에 한글명 저장 후 ngram tokenizer로 색인 | 초기 사전 구축 비용 있음 |

**결정**: `nameKo`, `seriesKo`, `setNameKo` 필드 + ngram 분석기

**ngram tokenizer 동작 원리**

```
색인 시점 (ngram_analyzer, min_gram=2):
"리자몽" → ["리자", "리자몽", "자몽"]
"반역크래시" → ["반역", "역크", "크래", "래시", "반역크", ..., "반역크래시"]

검색 시점 (standard analyzer):
"리자" → ["리자"]
"반역크래시" → ["반역크래시"]

→ 역색인에서 토큰 히트 → 해당 문서 반환
```

**min_gram = 2 선택 이유**: `min_gram = 1`이면 단일 글자 토큰이 생성되어 "리"만 입력해도 "리자몽", "리아코", "리아루" 등 모든 "리" 포함 카드가 전부 매칭되어 검색 노이즈가 과도하게 증가한다. 최소 2글자 이상 입력 시에만 의미 있는 매칭이 이루어지도록 제한.

### 5. 포켓몬 한글 사전 설계 (`PokemonNameDictionary`)

카드 데이터는 영어명(`Charizard`)으로 저장되므로, 카드 승인·동기화 시점에 한글명을 함께 저장해야 한다.

**데이터 수집**: PokeAPI에서 1,025종 전체 한글명 수집 → `src/main/resources/pokemon-names.yml`

```yaml
names:
  리자몽: Charizard
  폴리곤2: Porygon2
  미스터마임: Mr. Mime
  ...
```

**조회 알고리즘**: 다단어 포켓몬("Mr. Mime", "Tapu Koko") 대응을 위해 슬라이딩 윈도우(긴 구간 우선)로 조회

```
"Mr. Mime ex" 카드명 처리:
  len=3: normalize("Mr.Mimex") → "mrmimex" → ❌
  len=2: normalize("Mr.Mime")  → "mrmime"  → "미스터마임" ✅
```

**정규화 규칙** (`normalize()`):
- `toLowerCase(Locale.ROOT)`: 터키 로케일 등 환경에 무관한 일관된 결과 보장
- `replaceAll("[^a-z0-9]", "")`: 하이픈·점 등 특수문자 제거, **숫자는 보존**

  > 숫자 보존 이유: `[^a-z]`로 제거하면 `Porygon` → `porygon`, `Porygon2` → `porygon`으로 키 충돌 발생

**한계**: 트레이너·에너지 카드는 PokeAPI에 데이터 없음 → `nameKo = null` (영어명으로만 검색 가능)

### 6. 한글 시리즈·확장팩명 사전 설계

#### SeriesNameDictionary / SetNameDictionary

포켓몬 TCG 한국판은 일본판 세트 구조를 따르기 때문에 영문 TCGdex 세트명과 1:1 대응이 되지 않는 경우가 많다. 이를 해결하기 위해 한글명 → 영문 DB값 변환 사전을 별도로 관리한다.

```yaml
# series-names.yml
names:
  소드실드: Sword & Shield
  검과방패: Sword & Shield
  스칼렛바이올렛: Scarlet & Violet

# set-names.yml
names:
  반역크래시: Rebel Clash
  폭염워커: Vivid Voltage   # S2a (일본판) → 영문 직접 대응 없음, 근접 매핑
  ...
```

**두 가지 변환 방향**

| 메서드 | 방향 | 사용처 |
|--------|------|--------|
| `translate(input)` | 한글 → 영문 DB값 | 필터 파라미터 변환 (`?series=소드실드`) |
| `getKoreanText(englishName)` | 영문 DB값 → 한글 별칭 전체 | ES 인덱싱 시 `seriesKo`·`setNameKo` 필드 구성 |

**정규화 규칙**: 공백·특수문자 제거, 한글(가–힣)·영문·숫자만 유지
```
"소드 & 실드" → normalize → "소드실드" → 사전 조회 → "Sword & Shield"
```

**영문 직접 입력 허용**: 매핑이 없는 경우 원본값 반환 → 기존 영문 파라미터 클라이언트와 하위 호환

**getKoreanText 동작 예시**

```
enToKoText.get("Sword & Shield")
→ "검과방패 소드실드 소드앤실드"  (YAML에 등록된 모든 한글 별칭을 공백으로 연결)

→ seriesKo 필드에 저장 → ngram 색인
→ 사용자가 "소드실드" 또는 "검과방패" 어느 것으로 검색해도 매칭
```

### 7. 검색 쿼리 설계

```
GET /api/v1/cards?keyword=반역크래시 리자몽
GET /api/v1/cards?series=소드실드&grade=PSA_10
GET /api/v1/cards?keyword=리자몽&setName=반역크래시
```

ES BoolQuery 구조:

```json
{
  "bool": {
    "filter": [
      { "term": { "status": "ACTIVE" } },
      { "term": { "grade": "PSA_10" } }
    ],
    "must": [
      {
        "multi_match": {
          "query": "반역크래시 리자몽",
          "fields": ["name", "nameKo", "seriesKo", "setNameKo"],
          "type": "cross_fields",
          "operator": "and"
        }
      }
    ]
  }
}
```

**multi_match 설계 결정: `cross_fields` + `operator: AND`**

| 방식 | 동작 | 문제 |
|------|------|------|
| `best_fields` + OR (기본값) | 어느 한 필드에서라도 매칭되면 반환 | "반역크래시 리자몽" 검색 시 반역크래시 세트 전체(209건) + 리자몽 카드 전체가 반환됨 |
| **`cross_fields` + AND** (채택) | 모든 토큰이 필드 전체에 걸쳐 존재해야 반환 | 교집합 의미론: 반역크래시 세트에 있는 리자몽 카드만 반환 |

```
"반역크래시 리자몽" (cross_fields + AND):
  토큰 "반역크래시" → setNameKo에서 매칭
  토큰 "리자몽"     → nameKo에서 매칭
  → 두 조건 모두 충족하는 문서만 반환 (교집합)
```

**필터 파라미터와 keyword 조합 가능**:

```
?keyword=리자몽&series=소드실드&grade=PSA_10
  ├─ keyword "리자몽" → multi_match (must)
  ├─ series "소드실드" → translate → "Sword & Shield" → term filter
  └─ grade PSA_10 → term filter
```

**정렬 전략**:

| 상황 | 정렬 기준 | 이유 |
|------|---------|------|
| keyword 있음 | `_score DESC` | 관련도 높은 카드 우선 노출 |
| keyword 없음 | `createdAt DESC` | 최신 카드 우선 (브라우즈 모드) |

**keyword 최소 길이**: 1자 입력 시 ngram 토큰이 너무 많이 매칭되어 결과가 부정확하므로 2자 미만 입력 시 예외 처리.

### 8. ES 인덱싱 전략: after-commit 동기화

DB 트랜잭션 **커밋 이후**에 ES 동기화를 실행한다.

**문제**: 커밋 전 ES 동기화 시 DB rollback이 발생하면 ES에만 데이터가 남아 영구 불일치 발생

**해결**: `TransactionSynchronizationManager.registerSynchronization()`으로 after-commit 실행 보장

```
DB 트랜잭션 시작
    → DB 변경 (카드 승인/수정/삭제)
트랜잭션 커밋 성공
    → afterCommit(): ES 인덱싱/삭제 실행
```

ES 장애 시에도 DB 트랜잭션에 영향 없도록 try-catch로 격리. 실패는 warn 로그로만 기록.

**인덱싱 발생 시점**

| 시점 | 처리 |
|------|------|
| 관리자 카드 승인 | 인덱스 추가 |
| 관리자 카드 수정 (ACTIVE 상태) | 인덱스 갱신 |
| 관리자 카드 삭제 | 인덱스 삭제 |
| TCGdex 주간 자동 동기화 | 신규 카드 인덱스 추가 |
| 수동 마이그레이션 (`POST /api/v1/admin/cards/es-migrate`) | 전체 일괄 인덱싱 |

### 9. 일괄 마이그레이션: 배치 처리

ES 인덱스 재생성 시 전체 카드를 재인덱싱한다. 전체 카드를 한 번에 메모리에 적재하면 OOM 위험이 있으므로 500건 단위 배치로 분할 처리한다.

```
DB에서 500건 페이지 조회 → ES saveAll → 다음 페이지 → ... 완료
```

각 카드에 대해 `nameKo`, `seriesKo`, `setNameKo`를 사전에서 조회하여 함께 인덱싱한다.

### 10. Docker 환경 통합: 전체 서비스 자동 기동

기존 ADR-001에서 Kafka와 ES를 profile로 분리했으나, 두 서비스 모두 현재 프로젝트의 핵심 의존성이 되었다.

**변경**: `profiles: [kafka]`, `profiles: [es]` 제거 → 모든 서비스 기본 기동

**이유**:
- ES는 카드 검색의 핵심 의존성이 되어 ES 없이 카드 검색 API가 동작하지 않음
- Kafka는 알림·이벤트 처리의 핵심 의존성으로 대부분의 개발 시나리오에서 필요
- profile 플래그 관리로 인한 팀원 간 기동 방법 혼선 제거

```bash
# 변경 전
bash scripts/start.sh --es --kafka

# 변경 후
bash scripts/start.sh
```

---

## 결과 (Consequences)

### 긍정적 영향

- **한글 포켓몬명 부분 검색**: "리자" 입력 시 리자몽(Charizard) 카드 검색 가능
- **한글 시리즈·확장팩명 통합 검색**: "소드실드", "반역크래시" 등 한글 입력으로 keyword 검색 및 필터 모두 지원
- **교집합 검색**: "반역크래시 리자몽" 검색 시 반역크래시 세트의 리자몽 카드만 반환 (cross_fields AND)
- **검색 품질 향상**: keyword 검색 시 관련도(_score) 기반 정렬로 가장 관련성 높은 카드 우선 노출
- **확장성**: 향후 경매·게시글 검색에도 동일한 ES 인프라 재사용 가능
- **DB 부하 감소**: 대용량 검색 쿼리가 ES로 이관되어 MySQL은 트랜잭션 처리에 집중
- **하위 호환**: 영문 파라미터 직접 입력도 그대로 동작 (사전 미매칭 시 원본 반환)

### 부정적 영향 / 주의사항

- **인프라 복잡도 증가**: ES + Kibana 추가로 Docker 기동 시간 및 메모리 사용량 증가 (ES 최소 512MB)
- **DB-ES 동기화 지연**: after-commit 방식이므로 트랜잭션 커밋 후 수 ms의 검색 반영 지연 존재 (허용 가능 수준)
- **ES 장애 시 검색 불가**: ES 다운 시 카드 검색 API가 동작하지 않음. 단, 카드 단건 조회·관리 기능은 MySQL에서 정상 동작
- **트레이너·에너지 카드 한글 검색 불가**: PokeAPI 미지원으로 한글명 사전에 없음. 추후 수동 사전 확장 필요
- **일본판 전용 세트 근접 매핑**: 한국판이 일본판 구조를 따르므로 "폭염워커(S2a)" 같이 영문 TCGdex에 직접 대응이 없는 세트는 가장 근접한 영문 세트로 매핑 (주석으로 명시)
- **keyword 검색 공백 필요**: "반역크래시리자몽"처럼 붙여 쓰면 standard analyzer가 하나의 토큰으로 처리하여 의도대로 동작하지 않을 수 있음. 단어 간 공백 입력 필요
- **인덱스 재생성 시 수동 조작 필요**: ES 설정 변경(analyzer 등) 시 `DELETE /cards` → 백엔드 재시작 → `POST /api/v1/admin/cards/es-migrate` 수동 실행 필요
  - **중요**: `DELETE /cards`는 반드시 백엔드 재시작 **전에** 수행해야 함. 재시작 후 삭제하면 Spring Data ES가 인덱스를 재생성하지 않아 es-migrate 시 동적 매핑으로 잘못 생성됨
- **다중 인스턴스 배포 시 인덱싱 중복**: 여러 인스턴스에서 동일 카드를 동시에 인덱싱할 수 있으나 idempotent 연산이므로 결과 정합성은 유지됨

---

## 최초 배포 / 인덱스 재생성 절차

```bash
# 1. 기존 인덱스 삭제 (Kibana Dev Tools: http://localhost:5601)
DELETE /cards

# 2. JAR 재빌드 (코드 변경이 있는 경우)
./gradlew build -x test

# 3. 재시작 — 인덱스 자동 재생성 (ngram 설정 + @Field 매핑 적용)
docker compose up --build backend -d
# 또는 JAR 변경 없이 인덱스만 재생성 시:
docker compose restart backend

# 4. 매핑 확인 (Kibana Dev Tools)
GET /cards/_mapping
# seriesKo, setNameKo 필드에 ngram_analyzer 적용 확인

# 5. 전체 카드 마이그레이션 (Admin JWT 필요)
POST /api/v1/admin/cards/es-migrate
# 응답: { "data": 23193 }
```

---

## 관련 문서

- `src/main/java/.../card/document/CardDocument.java` — ES 문서 매핑 (`seriesKo`, `setNameKo` 포함)
- `src/main/java/.../card/service/CardQueryService.java` — ES 검색 쿼리 구현 (cross_fields + AND)
- `src/main/java/.../card/service/CardCommandService.java` — after-commit 인덱싱
- `src/main/java/.../card/service/CardEsMigrationService.java` — 배치 마이그레이션
- `src/main/java/.../card/util/PokemonNameDictionary.java` — 한글 포켓몬 사전
- `src/main/java/.../card/util/SeriesNameDictionary.java` — 한글 시리즈명 사전 (translate + getKoreanText)
- `src/main/java/.../card/util/SetNameDictionary.java` — 한글 확장팩명 사전 (translate + getKoreanText)
- `src/main/resources/es-settings/cards-settings.json` — ngram analyzer 설정
- `src/main/resources/pokemon-names.yml` — 포켓몬 한글명 사전 (1,025종)
- `src/main/resources/series-names.yml` — 시리즈 한글명 사전
- `src/main/resources/set-names.yml` — 확장팩 한글명 사전
- `scripts/fetch-pokemon-names.py` — PokeAPI 한글명 수집 스크립트
- `docs/adr/ADR-001-docker-local-dev-setup.md` — Docker 환경 원본 설계 (profile 분리 배경)
