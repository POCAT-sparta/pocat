# Domain Separation (card → card/series/set/pokemon) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** `domain/card` 안에 섞여 있는 Series/Set/Pokemon을 독립 JPA 엔티티로 분리하고, 한글명(nameKo)을 DB에서 관리해 ES 재인덱싱 없이 Admin API로 수정 가능하게 한다.

**Architecture:** Flyway V2로 신규 테이블 생성 + cards 테이블 스키마 교체(FK 컬럼 추가, 구 String 컬럼 DROP)를 원자적으로 수행한다. 그 전에 Series/PokemonSet/Pokemon 엔티티와 서비스를 먼저 작성해두고, 마지막에 Card 엔티티와 기존 서비스들을 한 번에 마이그레이션한다.

**Tech Stack:** Spring Boot 3, JPA/Hibernate (ddl-auto:update), Flyway, Elasticsearch (Spring Data ES), JUnit 5 + Mockito

---

## 파일 구조

### 신규 생성
```
domain/series/entity/Series.java
domain/series/repository/SeriesRepository.java
domain/series/service/SeriesCommandService.java
domain/series/service/SeriesQueryService.java
domain/series/dto/request/UpsertSeriesRequest.java
domain/series/dto/response/SeriesResponse.java
domain/series/controller/AdminSeriesController.java

domain/set/entity/PokemonSet.java
domain/set/repository/PokemonSetRepository.java
domain/set/service/PokemonSetCommandService.java
domain/set/service/PokemonSetQueryService.java
domain/set/dto/request/UpsertPokemonSetRequest.java
domain/set/dto/response/PokemonSetResponse.java
domain/set/controller/AdminPokemonSetController.java

domain/pokemon/entity/Pokemon.java
domain/pokemon/repository/PokemonRepository.java
domain/pokemon/service/PokemonCommandService.java
domain/pokemon/service/PokemonQueryService.java
domain/pokemon/service/DomainDataSeeder.java
domain/pokemon/dto/request/UpsertPokemonRequest.java
domain/pokemon/dto/response/PokemonResponse.java
domain/pokemon/controller/AdminPokemonController.java

resources/db/migration/V2__domain_separation.sql

test/.../domain/series/service/SeriesCommandServiceTest.java
test/.../domain/set/service/PokemonSetCommandServiceTest.java
test/.../domain/pokemon/service/PokemonCommandServiceTest.java
```

### 수정
```
global/exception/common/ErrorCode.java               (에러코드 추가)
domain/card/entity/Card.java                         (String 필드 제거, @ManyToOne 추가)
domain/card/dto/response/CardResponse.java           (from(Card) 수정)
domain/card/document/CardDocument.java               (from(Card,...) 수정)
domain/card/service/CardCommandService.java          (find-or-create 연결)
domain/card/service/CardSyncService.java             (find-or-create 연결)
domain/card/service/CardQueryService.java            (Dictionary → 엔티티 조회)
domain/card/service/CardEsMigrationService.java      (Dictionary → 엔티티 조회)
domain/card/repository/CardRepository.java           (pokemon 카드 조회 쿼리 추가)
test/support/TestFixtures.java                       (Series/PokemonSet/Pokemon fixture 추가)
```

### 삭제
```
domain/card/util/SeriesNameDictionary.java
domain/card/util/SetNameDictionary.java
domain/card/util/PokemonNameDictionary.java
```

---

## Task 1: ErrorCode + 신규 엔티티 3종 + Repository

**Files:**
- Modify: `src/main/java/com/rocketcrew/pocat/global/exception/common/ErrorCode.java`
- Create: `src/main/java/com/rocketcrew/pocat/domain/series/entity/Series.java`
- Create: `src/main/java/com/rocketcrew/pocat/domain/set/entity/PokemonSet.java`
- Create: `src/main/java/com/rocketcrew/pocat/domain/pokemon/entity/Pokemon.java`
- Create: `src/main/java/com/rocketcrew/pocat/domain/series/repository/SeriesRepository.java`
- Create: `src/main/java/com/rocketcrew/pocat/domain/set/repository/PokemonSetRepository.java`
- Create: `src/main/java/com/rocketcrew/pocat/domain/pokemon/repository/PokemonRepository.java`

- [ ] **Step 1: ErrorCode에 Series / PokemonSet / Pokemon 에러코드 추가**

`ErrorCode.java`의 `// Card` 블록 아래에 추가:

```java
// Series
SERIES_NOT_FOUND(HttpStatus.NOT_FOUND, "시리즈를 찾을 수 없습니다."),

// PokemonSet
POKEMON_SET_NOT_FOUND(HttpStatus.NOT_FOUND, "확장팩을 찾을 수 없습니다."),

// Pokemon
POKEMON_NOT_FOUND(HttpStatus.NOT_FOUND, "포켓몬을 찾을 수 없습니다."),
```

- [ ] **Step 2: Series 엔티티 생성**

`src/main/java/com/rocketcrew/pocat/domain/series/entity/Series.java`:

```java
package com.rocketcrew.pocat.domain.series.entity;

import com.rocketcrew.pocat.global.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Builder
@AllArgsConstructor
@Entity
@Table(name = "series",
        uniqueConstraints = @UniqueConstraint(columnNames = "name"))
public class Series extends BaseEntity {

    @Column(name = "name", nullable = false, length = 100)
    private String name;      // "Sword & Shield" (TCGdex 영문명)

    @Column(name = "name_ko", length = 500)
    private String nameKo;    // "검과방패 소드실드 소드앤실드" (공백 구분 한글 별칭)

    public void updateNameKo(String nameKo) {
        this.nameKo = nameKo;
    }
}
```

- [ ] **Step 3: PokemonSet 엔티티 생성**

`src/main/java/com/rocketcrew/pocat/domain/set/entity/PokemonSet.java`:

```java
package com.rocketcrew.pocat.domain.set.entity;

import com.rocketcrew.pocat.domain.series.entity.Series;
import com.rocketcrew.pocat.global.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Builder
@AllArgsConstructor
@Entity
@Table(name = "pokemon_sets",
        uniqueConstraints = @UniqueConstraint(columnNames = "set_id"))
public class PokemonSet extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "series_id")
    private Series series;

    @Column(name = "set_id", nullable = false, length = 50)
    private String setId;     // "swsh5" (TCGdex ID)

    @Column(name = "name", nullable = false, length = 100)
    private String name;      // "Rebel Clash"

    @Column(name = "name_ko", length = 1000)
    private String nameKo;    // "반역크래시" (공백 구분 한글 별칭)

    public void updateNameKo(String nameKo) {
        this.nameKo = nameKo;
    }
}
```

- [ ] **Step 4: Pokemon 엔티티 생성**

`src/main/java/com/rocketcrew/pocat/domain/pokemon/entity/Pokemon.java`:

```java
package com.rocketcrew.pocat.domain.pokemon.entity;

import com.rocketcrew.pocat.global.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Builder
@AllArgsConstructor
@Entity
@Table(name = "pokemon",
        uniqueConstraints = @UniqueConstraint(columnNames = "name"))
public class Pokemon extends BaseEntity {

    @Column(name = "name", nullable = false, length = 100)
    private String name;     // "Charizard"

    @Column(name = "name_ko", length = 100)
    private String nameKo;   // "리자몽"

    public void updateNameKo(String nameKo) {
        this.nameKo = nameKo;
    }
}
```

- [ ] **Step 5: Repository 3개 생성**

`SeriesRepository.java`:
```java
package com.rocketcrew.pocat.domain.series.repository;

import com.rocketcrew.pocat.domain.series.entity.Series;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface SeriesRepository extends JpaRepository<Series, Long> {
    Optional<Series> findByName(String name);
    boolean existsByName(String name);
}
```

`PokemonSetRepository.java`:
```java
package com.rocketcrew.pocat.domain.set.repository;

import com.rocketcrew.pocat.domain.set.entity.PokemonSet;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface PokemonSetRepository extends JpaRepository<PokemonSet, Long> {
    Optional<PokemonSet> findBySetId(String setId);
}
```

`PokemonRepository.java`:
```java
package com.rocketcrew.pocat.domain.pokemon.repository;

import com.rocketcrew.pocat.domain.pokemon.entity.Pokemon;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface PokemonRepository extends JpaRepository<Pokemon, Long> {
    Optional<Pokemon> findByName(String name);
}
```

- [ ] **Step 6: 컴파일 확인**

```bash
./gradlew compileJava
```

Expected: BUILD SUCCESSFUL (기존 Card 도메인에 변경 없음, 신규 클래스만 추가)

- [ ] **Step 7: Commit**

```bash
git add src/main/java/com/rocketcrew/pocat/domain/series \
        src/main/java/com/rocketcrew/pocat/domain/set \
        src/main/java/com/rocketcrew/pocat/domain/pokemon \
        src/main/java/com/rocketcrew/pocat/global/exception/common/ErrorCode.java
git commit -m "feat: add Series, PokemonSet, Pokemon entities and repositories"
```

---

## Task 2: SeriesCommandService + SeriesQueryService

**Files:**
- Create: `src/main/java/com/rocketcrew/pocat/domain/series/service/SeriesCommandService.java`
- Create: `src/main/java/com/rocketcrew/pocat/domain/series/service/SeriesQueryService.java`
- Create: `src/main/java/com/rocketcrew/pocat/domain/series/dto/request/UpsertSeriesRequest.java`
- Create: `src/main/java/com/rocketcrew/pocat/domain/series/dto/response/SeriesResponse.java`
- Test: `src/test/java/com/rocketcrew/pocat/domain/series/service/SeriesCommandServiceTest.java`

- [ ] **Step 1: DTO 생성**

`UpsertSeriesRequest.java`:
```java
package com.rocketcrew.pocat.domain.series.dto.request;

import jakarta.validation.constraints.NotBlank;

public record UpsertSeriesRequest(
        @NotBlank String name,
        String nameKo
) {}
```

`SeriesResponse.java`:
```java
package com.rocketcrew.pocat.domain.series.dto.response;

import com.rocketcrew.pocat.domain.series.entity.Series;

public record SeriesResponse(
        Long id,
        String name,
        String nameKo
) {
    public static SeriesResponse from(Series s) {
        return new SeriesResponse(s.getId(), s.getName(), s.getNameKo());
    }
}
```

- [ ] **Step 2: SeriesCommandService 생성**

`SeriesCommandService.java`:
```java
package com.rocketcrew.pocat.domain.series.service;

import com.rocketcrew.pocat.domain.series.dto.request.UpsertSeriesRequest;
import com.rocketcrew.pocat.domain.series.dto.response.SeriesResponse;
import com.rocketcrew.pocat.domain.series.entity.Series;
import com.rocketcrew.pocat.domain.series.repository.SeriesRepository;
import com.rocketcrew.pocat.global.exception.common.ErrorCode;
import com.rocketcrew.pocat.global.exception.domain.SeriesException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class SeriesCommandService {

    private final SeriesRepository seriesRepository;

    /** Card 등록/동기화 시 호출. 이미 존재하면 그대로 반환 */
    public Series findOrCreate(String name) {
        return seriesRepository.findByName(name)
                .orElseGet(() -> seriesRepository.save(
                        Series.builder().name(name).build()));
    }

    public SeriesResponse create(UpsertSeriesRequest request) {
        Series series = seriesRepository.save(
                Series.builder()
                        .name(request.name())
                        .nameKo(request.nameKo())
                        .build());
        return SeriesResponse.from(series);
    }

    public SeriesResponse updateNameKo(Long id, String nameKo) {
        Series series = seriesRepository.findById(id)
                .orElseThrow(() -> new SeriesException(ErrorCode.SERIES_NOT_FOUND));
        series.updateNameKo(nameKo);
        return SeriesResponse.from(series);
    }

    public void delete(Long id) {
        Series series = seriesRepository.findById(id)
                .orElseThrow(() -> new SeriesException(ErrorCode.SERIES_NOT_FOUND));
        seriesRepository.delete(series);
    }
}
```

- [ ] **Step 3: SeriesException 생성**

`src/main/java/com/rocketcrew/pocat/global/exception/domain/SeriesException.java`:
```java
package com.rocketcrew.pocat.global.exception.domain;

import com.rocketcrew.pocat.global.exception.common.ErrorCode;
import lombok.Getter;

@Getter
public class SeriesException extends RuntimeException {
    private final ErrorCode errorCode;
    public SeriesException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
    }
}
```

같은 패턴으로 `PokemonSetException.java`, `PokemonException.java`도 생성:
```java
// PokemonSetException — package: global.exception.domain, 내용 SeriesException과 동일 구조
// PokemonException   — 동일
```

- [ ] **Step 4: SeriesQueryService 생성**

`SeriesQueryService.java`:
```java
package com.rocketcrew.pocat.domain.series.service;

import com.rocketcrew.pocat.domain.series.dto.response.SeriesResponse;
import com.rocketcrew.pocat.domain.series.entity.Series;
import com.rocketcrew.pocat.domain.series.repository.SeriesRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SeriesQueryService {

    private final SeriesRepository seriesRepository;

    public List<SeriesResponse> findAll() {
        return seriesRepository.findAll().stream()
                .map(SeriesResponse::from).toList();
    }

    /**
     * 한글(또는 영문) 시리즈명 → DB 영문 시리즈명.
     * ES term 필터용. 매핑 없으면 원본 반환 (영문 직접 입력 호환).
     */
    public String translate(String input) {
        if (input == null || input.isBlank()) return input;
        // 영문 그대로 통과
        if (seriesRepository.existsByName(input)) return input;
        // 한글 → nameKo 공백 분리 별칭 매칭
        String normalized = normalize(input);
        return seriesRepository.findAll().stream()
                .filter(s -> s.getNameKo() != null &&
                             Arrays.stream(s.getNameKo().split("\\s+"))
                                   .anyMatch(alias -> normalize(alias).equals(normalized)))
                .findFirst()
                .map(Series::getName)
                .orElse(input);
    }

    private static String normalize(String s) {
        return s.replaceAll("[^가-힣a-zA-Z0-9]", "");
    }
}
```

- [ ] **Step 5: SeriesCommandServiceTest 작성 후 실행**

`src/test/java/com/rocketcrew/pocat/domain/series/service/SeriesCommandServiceTest.java`:
```java
package com.rocketcrew.pocat.domain.series.service;

import com.rocketcrew.pocat.domain.series.entity.Series;
import com.rocketcrew.pocat.domain.series.repository.SeriesRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class SeriesCommandServiceTest {

    @InjectMocks SeriesCommandService seriesCommandService;
    @Mock SeriesRepository seriesRepository;

    @Test
    @DisplayName("findOrCreate: 존재하는 시리즈면 저장 없이 반환")
    void findOrCreate_existing() {
        Series existing = Series.builder().name("Sword & Shield").build();
        ReflectionTestUtils.setField(existing, "id", 1L);
        given(seriesRepository.findByName("Sword & Shield")).willReturn(Optional.of(existing));

        Series result = seriesCommandService.findOrCreate("Sword & Shield");

        assertThat(result.getName()).isEqualTo("Sword & Shield");
        verify(seriesRepository, never()).save(any());
    }

    @Test
    @DisplayName("findOrCreate: 없는 시리즈면 INSERT 후 반환")
    void findOrCreate_new() {
        Series saved = Series.builder().name("New Series").build();
        ReflectionTestUtils.setField(saved, "id", 2L);
        given(seriesRepository.findByName("New Series")).willReturn(Optional.empty());
        given(seriesRepository.save(any())).willReturn(saved);

        Series result = seriesCommandService.findOrCreate("New Series");

        assertThat(result.getName()).isEqualTo("New Series");
        verify(seriesRepository).save(any());
    }
}
```

Run:
```bash
./gradlew test --tests "*.SeriesCommandServiceTest"
```

Expected: 2 tests PASSED

- [ ] **Step 6: Commit**

```bash
git add src/main/java/com/rocketcrew/pocat/domain/series \
        src/main/java/com/rocketcrew/pocat/global/exception/domain/SeriesException.java \
        src/test/java/com/rocketcrew/pocat/domain/series
git commit -m "feat: add SeriesCommandService, SeriesQueryService"
```

---

## Task 3: PokemonSetCommandService + PokemonSetQueryService

**Files:**
- Create: `domain/set/service/PokemonSetCommandService.java`
- Create: `domain/set/service/PokemonSetQueryService.java`
- Create: `domain/set/dto/request/UpsertPokemonSetRequest.java`
- Create: `domain/set/dto/response/PokemonSetResponse.java`
- Create: `global/exception/domain/PokemonSetException.java`
- Test: `domain/set/service/PokemonSetCommandServiceTest.java`

- [ ] **Step 1: DTO 생성**

`UpsertPokemonSetRequest.java`:
```java
package com.rocketcrew.pocat.domain.set.dto.request;
import jakarta.validation.constraints.NotBlank;

public record UpsertPokemonSetRequest(
        @NotBlank String setId,
        @NotBlank String name,
        String nameKo,
        Long seriesId
) {}
```

`PokemonSetResponse.java`:
```java
package com.rocketcrew.pocat.domain.set.dto.response;
import com.rocketcrew.pocat.domain.set.entity.PokemonSet;

public record PokemonSetResponse(
        Long id,
        String setId,
        String name,
        String nameKo,
        Long seriesId
) {
    public static PokemonSetResponse from(PokemonSet ps) {
        return new PokemonSetResponse(
                ps.getId(), ps.getSetId(), ps.getName(), ps.getNameKo(),
                ps.getSeries() != null ? ps.getSeries().getId() : null);
    }
}
```

- [ ] **Step 2: PokemonSetCommandService 생성**

`PokemonSetCommandService.java`:
```java
package com.rocketcrew.pocat.domain.set.service;

import com.rocketcrew.pocat.domain.series.entity.Series;
import com.rocketcrew.pocat.domain.set.dto.request.UpsertPokemonSetRequest;
import com.rocketcrew.pocat.domain.set.dto.response.PokemonSetResponse;
import com.rocketcrew.pocat.domain.set.entity.PokemonSet;
import com.rocketcrew.pocat.domain.set.repository.PokemonSetRepository;
import com.rocketcrew.pocat.global.exception.common.ErrorCode;
import com.rocketcrew.pocat.global.exception.domain.PokemonSetException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class PokemonSetCommandService {

    private final PokemonSetRepository pokemonSetRepository;

    /** Card 등록/동기화 시 호출. setId 기준으로 find-or-create */
    public PokemonSet findOrCreate(String setId, String setName, Series series) {
        return pokemonSetRepository.findBySetId(setId)
                .orElseGet(() -> pokemonSetRepository.save(
                        PokemonSet.builder()
                                .setId(setId)
                                .name(setName)
                                .series(series)
                                .build()));
    }

    public PokemonSetResponse create(UpsertPokemonSetRequest request, Series series) {
        PokemonSet ps = pokemonSetRepository.save(
                PokemonSet.builder()
                        .setId(request.setId())
                        .name(request.name())
                        .nameKo(request.nameKo())
                        .series(series)
                        .build());
        return PokemonSetResponse.from(ps);
    }

    public PokemonSetResponse updateNameKo(Long id, String nameKo) {
        PokemonSet ps = pokemonSetRepository.findById(id)
                .orElseThrow(() -> new PokemonSetException(ErrorCode.POKEMON_SET_NOT_FOUND));
        ps.updateNameKo(nameKo);
        return PokemonSetResponse.from(ps);
    }

    public void delete(Long id) {
        PokemonSet ps = pokemonSetRepository.findById(id)
                .orElseThrow(() -> new PokemonSetException(ErrorCode.POKEMON_SET_NOT_FOUND));
        pokemonSetRepository.delete(ps);
    }
}
```

- [ ] **Step 3: PokemonSetQueryService 생성**

`PokemonSetQueryService.java`:
```java
package com.rocketcrew.pocat.domain.set.service;

import com.rocketcrew.pocat.domain.set.dto.response.PokemonSetResponse;
import com.rocketcrew.pocat.domain.set.entity.PokemonSet;
import com.rocketcrew.pocat.domain.set.repository.PokemonSetRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PokemonSetQueryService {

    private final PokemonSetRepository pokemonSetRepository;

    public List<PokemonSetResponse> findAll() {
        return pokemonSetRepository.findAll().stream()
                .map(PokemonSetResponse::from).toList();
    }

    /** 한글(또는 영문) 확장팩명 → DB 영문 set name. ES term 필터용 */
    public String translate(String input) {
        if (input == null || input.isBlank()) return input;
        String normalized = normalize(input);
        return pokemonSetRepository.findAll().stream()
                .filter(ps -> {
                    if (normalize(ps.getName()).equals(normalized)) return true;
                    return ps.getNameKo() != null &&
                           Arrays.stream(ps.getNameKo().split("\\s+"))
                                 .anyMatch(alias -> normalize(alias).equals(normalized));
                })
                .findFirst()
                .map(PokemonSet::getName)
                .orElse(input);
    }

    private static String normalize(String s) {
        return s.replaceAll("[^가-힣a-zA-Z0-9]", "");
    }
}
```

- [ ] **Step 4: 테스트 작성 후 실행**

`PokemonSetCommandServiceTest.java`:
```java
package com.rocketcrew.pocat.domain.set.service;

import com.rocketcrew.pocat.domain.series.entity.Series;
import com.rocketcrew.pocat.domain.set.entity.PokemonSet;
import com.rocketcrew.pocat.domain.set.repository.PokemonSetRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class PokemonSetCommandServiceTest {

    @InjectMocks PokemonSetCommandService pokemonSetCommandService;
    @Mock PokemonSetRepository pokemonSetRepository;

    @Test
    @DisplayName("findOrCreate: 존재하는 setId면 저장 없이 반환")
    void findOrCreate_existing() {
        PokemonSet existing = PokemonSet.builder().setId("swsh5").name("Rebel Clash").build();
        ReflectionTestUtils.setField(existing, "id", 1L);
        given(pokemonSetRepository.findBySetId("swsh5")).willReturn(Optional.of(existing));

        Series series = Series.builder().name("Sword & Shield").build();
        PokemonSet result = pokemonSetCommandService.findOrCreate("swsh5", "Rebel Clash", series);

        assertThat(result.getSetId()).isEqualTo("swsh5");
        verify(pokemonSetRepository, never()).save(any());
    }

    @Test
    @DisplayName("findOrCreate: 없는 setId면 INSERT 후 반환")
    void findOrCreate_new() {
        PokemonSet saved = PokemonSet.builder().setId("sv1").name("Scarlet & Violet").build();
        ReflectionTestUtils.setField(saved, "id", 2L);
        given(pokemonSetRepository.findBySetId("sv1")).willReturn(Optional.empty());
        given(pokemonSetRepository.save(any())).willReturn(saved);

        Series series = Series.builder().name("Scarlet & Violet").build();
        PokemonSet result = pokemonSetCommandService.findOrCreate("sv1", "Scarlet & Violet", series);

        assertThat(result.getName()).isEqualTo("Scarlet & Violet");
        verify(pokemonSetRepository).save(any());
    }
}
```

Run:
```bash
./gradlew test --tests "*.PokemonSetCommandServiceTest"
```

Expected: 2 tests PASSED

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/rocketcrew/pocat/domain/set \
        src/main/java/com/rocketcrew/pocat/global/exception/domain/PokemonSetException.java \
        src/test/java/com/rocketcrew/pocat/domain/set
git commit -m "feat: add PokemonSetCommandService, PokemonSetQueryService"
```

---

## Task 4: PokemonCommandService + 테스트

**Files:**
- Create: `domain/pokemon/service/PokemonCommandService.java`
- Create: `domain/pokemon/service/PokemonQueryService.java`
- Create: `domain/pokemon/dto/request/UpsertPokemonRequest.java`
- Create: `domain/pokemon/dto/response/PokemonResponse.java`
- Create: `global/exception/domain/PokemonException.java`
- Test: `domain/pokemon/service/PokemonCommandServiceTest.java`

- [ ] **Step 1: DTO + Exception 생성**

`UpsertPokemonRequest.java`:
```java
package com.rocketcrew.pocat.domain.pokemon.dto.request;
import jakarta.validation.constraints.NotBlank;
public record UpsertPokemonRequest(@NotBlank String name, String nameKo) {}
```

`PokemonResponse.java`:
```java
package com.rocketcrew.pocat.domain.pokemon.dto.response;
import com.rocketcrew.pocat.domain.pokemon.entity.Pokemon;
public record PokemonResponse(Long id, String name, String nameKo) {
    public static PokemonResponse from(Pokemon p) {
        return new PokemonResponse(p.getId(), p.getName(), p.getNameKo());
    }
}
```

`PokemonException.java` (같은 패턴으로):
```java
package com.rocketcrew.pocat.global.exception.domain;
import com.rocketcrew.pocat.global.exception.common.ErrorCode;
import lombok.Getter;

@Getter
public class PokemonException extends RuntimeException {
    private final ErrorCode errorCode;
    public PokemonException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
    }
}
```

- [ ] **Step 2: PokemonCommandService 생성**

`PokemonCommandService.java`:
```java
package com.rocketcrew.pocat.domain.pokemon.service;

import com.rocketcrew.pocat.domain.pokemon.dto.request.UpsertPokemonRequest;
import com.rocketcrew.pocat.domain.pokemon.dto.response.PokemonResponse;
import com.rocketcrew.pocat.domain.pokemon.entity.Pokemon;
import com.rocketcrew.pocat.domain.pokemon.repository.PokemonRepository;
import com.rocketcrew.pocat.global.exception.common.ErrorCode;
import com.rocketcrew.pocat.global.exception.domain.PokemonException;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class PokemonCommandService {

    private final PokemonRepository pokemonRepository;

    /** 정규화된 영문 포켓몬명 → Pokemon 인메모리 캐시 */
    private final Map<String, Pokemon> nameCache = new ConcurrentHashMap<>();

    @PostConstruct
    void buildCache() {
        pokemonRepository.findAll().forEach(p -> nameCache.put(normalize(p.getName()), p));
        log.info("[PokemonCache] {}개 포켓몬 캐시 로드", nameCache.size());
    }

    /** 카드명("Charizard ex")에서 슬라이딩 윈도우로 포켓몬명 추출 후 find-or-create */
    public Optional<Pokemon> findOrCreateForCardName(String cardName) {
        if (cardName == null) return Optional.empty();
        String[] words = cardName.split("\\s+");
        for (int len = words.length; len >= 1; len--) {
            for (int start = 0; start <= words.length - len; start++) {
                String candidate = String.join("", Arrays.copyOfRange(words, start, start + len));
                Pokemon cached = nameCache.get(normalize(candidate));
                if (cached != null) return Optional.of(cached);
            }
        }
        return Optional.empty();
    }

    /** pokemon-names.yml 에서 읽어온 (nameEn, nameKo) 쌍으로 일괄 저장 */
    public Pokemon findOrCreate(String name, String nameKo) {
        return pokemonRepository.findByName(name).orElseGet(() -> {
            Pokemon saved = pokemonRepository.save(
                    Pokemon.builder().name(name).nameKo(nameKo).build());
            nameCache.put(normalize(name), saved);
            return saved;
        });
    }

    public PokemonResponse updateNameKo(Long id, String nameKo) {
        Pokemon pokemon = pokemonRepository.findById(id)
                .orElseThrow(() -> new PokemonException(ErrorCode.POKEMON_NOT_FOUND));
        pokemon.updateNameKo(nameKo);
        nameCache.put(normalize(pokemon.getName()), pokemon);
        return PokemonResponse.from(pokemon);
    }

    public void delete(Long id) {
        Pokemon pokemon = pokemonRepository.findById(id)
                .orElseThrow(() -> new PokemonException(ErrorCode.POKEMON_NOT_FOUND));
        nameCache.remove(normalize(pokemon.getName()));
        pokemonRepository.delete(pokemon);
    }

    private static String normalize(String word) {
        return word.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
    }
}
```

- [ ] **Step 3: PokemonQueryService 생성**

`PokemonQueryService.java`:
```java
package com.rocketcrew.pocat.domain.pokemon.service;

import com.rocketcrew.pocat.domain.pokemon.dto.response.PokemonResponse;
import com.rocketcrew.pocat.domain.pokemon.repository.PokemonRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PokemonQueryService {

    private final PokemonRepository pokemonRepository;

    public List<PokemonResponse> findAll() {
        return pokemonRepository.findAll().stream().map(PokemonResponse::from).toList();
    }
}
```

- [ ] **Step 4: 테스트 작성 후 실행**

`PokemonCommandServiceTest.java`:
```java
package com.rocketcrew.pocat.domain.pokemon.service;

import com.rocketcrew.pocat.domain.pokemon.entity.Pokemon;
import com.rocketcrew.pocat.domain.pokemon.repository.PokemonRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class PokemonCommandServiceTest {

    @InjectMocks PokemonCommandService pokemonCommandService;
    @Mock PokemonRepository pokemonRepository;

    @BeforeEach
    void setup() {
        Pokemon charizard = Pokemon.builder().name("Charizard").nameKo("리자몽").build();
        ReflectionTestUtils.setField(charizard, "id", 1L);
        Pokemon mrMime = Pokemon.builder().name("Mr. Mime").nameKo("마임맨").build();
        ReflectionTestUtils.setField(mrMime, "id", 2L);
        given(pokemonRepository.findAll()).willReturn(List.of(charizard, mrMime));
        // @PostConstruct 수동 호출
        pokemonCommandService.buildCache();
    }

    @Test
    @DisplayName("슬라이딩 윈도우: 'Charizard ex'에서 Charizard 매칭")
    void findOrCreateForCardName_sliding() {
        Optional<Pokemon> result = pokemonCommandService.findOrCreateForCardName("Charizard ex");
        assertThat(result).isPresent();
        assertThat(result.get().getName()).isEqualTo("Charizard");
    }

    @Test
    @DisplayName("슬라이딩 윈도우: 'Mr. Mime' 다단어 포켓몬 매칭")
    void findOrCreateForCardName_multiword() {
        Optional<Pokemon> result = pokemonCommandService.findOrCreateForCardName("Mr. Mime V");
        assertThat(result).isPresent();
        assertThat(result.get().getName()).isEqualTo("Mr. Mime");
    }

    @Test
    @DisplayName("슬라이딩 윈도우: 매칭 없으면 empty 반환")
    void findOrCreateForCardName_noMatch() {
        Optional<Pokemon> result = pokemonCommandService.findOrCreateForCardName("Professor's Research");
        assertThat(result).isEmpty();
    }
}
```

Run:
```bash
./gradlew test --tests "*.PokemonCommandServiceTest"
```

Expected: 3 tests PASSED

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/rocketcrew/pocat/domain/pokemon \
        src/main/java/com/rocketcrew/pocat/global/exception/domain/PokemonException.java \
        src/test/java/com/rocketcrew/pocat/domain/pokemon
git commit -m "feat: add PokemonCommandService with sliding-window card name lookup"
```

---

## Task 5: DomainDataSeeder (ApplicationReadyEvent)

**Files:**
- Create: `src/main/java/com/rocketcrew/pocat/domain/pokemon/service/DomainDataSeeder.java`
- Modify: `src/main/java/com/rocketcrew/pocat/domain/card/repository/CardRepository.java`

- [ ] **Step 1: CardRepository에 pokemon 카드 조회 쿼리 추가**

`CardRepository.java`에 추가:
```java
@Query("SELECT c FROM Card c WHERE c.category = com.rocketcrew.pocat.domain.card.entity.enums.CardCategory.POKEMON AND c.pokemon IS NULL")
List<Card> findPokemonCardsWithNullPokemon();
```

> 주의: `c.pokemon IS NULL`은 Task 6에서 Card 엔티티에 `pokemon` 필드가 추가된 후 컴파일된다. Task 5 커밋 전에 Task 6가 먼저 완료되어야 한다. 따라서 실제 작업 순서는 **Task 6 → Task 5** 순서로 진행한다.

- [ ] **Step 2: DomainDataSeeder 생성**

`DomainDataSeeder.java`:
```java
package com.rocketcrew.pocat.domain.pokemon.service;

import com.rocketcrew.pocat.domain.card.entity.Card;
import com.rocketcrew.pocat.domain.card.repository.CardRepository;
import com.rocketcrew.pocat.domain.pokemon.entity.Pokemon;
import com.rocketcrew.pocat.domain.series.entity.Series;
import com.rocketcrew.pocat.domain.series.repository.SeriesRepository;
import com.rocketcrew.pocat.domain.set.entity.PokemonSet;
import com.rocketcrew.pocat.domain.set.repository.PokemonSetRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.yaml.snakeyaml.Yaml;

import java.io.InputStream;
import java.util.*;

@Slf4j
@Component
@RequiredArgsConstructor
public class DomainDataSeeder {

    private final SeriesRepository seriesRepository;
    private final PokemonSetRepository pokemonSetRepository;
    private final PokemonCommandService pokemonCommandService;
    private final CardRepository cardRepository;

    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void seed() {
        enrichSeriesNameKo();
        enrichPokemonSetNameKo();
        seedPokemon();
        linkPokemonToCards();
    }

    /** series-names.yml → Series.nameKo 업데이트 */
    private void enrichSeriesNameKo() {
        Map<String, List<String>> enToKoList = loadYamlKoToEn("/series-names.yml");
        enToKoList.forEach((en, koList) -> {
            String joined = String.join(" ", koList);
            seriesRepository.findByName(en).ifPresent(s -> {
                s.updateNameKo(joined);
                log.info("[Seeder] Series nameKo 업데이트: {} → {}", en, joined);
            });
        });
    }

    /** set-names.yml → PokemonSet.nameKo 업데이트 */
    private void enrichPokemonSetNameKo() {
        Map<String, List<String>> enToKoList = loadYamlKoToEn("/set-names.yml");
        enToKoList.forEach((en, koList) -> {
            String joined = String.join(" ", koList);
            pokemonSetRepository.findAll().stream()
                    .filter(ps -> ps.getName().equals(en))
                    .forEach(ps -> {
                        ps.updateNameKo(joined);
                        log.info("[Seeder] PokemonSet nameKo 업데이트: {} → {}", en, joined);
                    });
        });
    }

    /** pokemon-names.yml → Pokemon 테이블 시드 (이미 있으면 스킵) */
    private void seedPokemon() {
        if (pokemonCommandService.getCacheSize() > 0) {
            log.info("[Seeder] Pokemon 이미 {}개 로드됨, 시드 스킵", pokemonCommandService.getCacheSize());
            return;
        }
        try (InputStream is = getClass().getResourceAsStream("/pokemon-names.yml")) {
            if (is == null) return;
            Map<String, Map<String, String>> root = new Yaml().load(is);
            Map<String, String> koToEn = root.getOrDefault("names", Collections.emptyMap());
            // nameKo → nameEn 역전: 같은 EN에 여러 KO가 있을 수 있으므로 first-win
            Map<String, String> enToKo = new LinkedHashMap<>();
            koToEn.forEach((ko, en) -> enToKo.putIfAbsent(en, ko));
            enToKo.forEach((en, ko) -> pokemonCommandService.findOrCreate(en, ko));
            log.info("[Seeder] Pokemon {}개 시드 완료", enToKo.size());
        } catch (Exception e) {
            log.warn("[Seeder] pokemon-names.yml 로드 실패: {}", e.getMessage());
        }
        pokemonCommandService.buildCache();
    }

    /** POKEMON 카드 중 pokemon_id 미설정 카드에 pokemon 연결 */
    private void linkPokemonToCards() {
        List<Card> cards = cardRepository.findPokemonCardsWithNullPokemon();
        int linked = 0;
        for (Card card : cards) {
            Optional<Pokemon> pokemon = pokemonCommandService.findOrCreateForCardName(card.getName());
            if (pokemon.isPresent()) {
                card.linkPokemon(pokemon.get());
                linked++;
            }
        }
        log.info("[Seeder] {}개 카드 pokemon 연결 완료 (전체 미연결: {}개)", linked, cards.size());
    }

    /**
     * YAML 파일에서 ko → en 맵을 로드하고, en → [ko, ko2, ...] 역방향 맵으로 변환
     */
    private Map<String, List<String>> loadYamlKoToEn(String resource) {
        Map<String, List<String>> enToKoList = new LinkedHashMap<>();
        try (InputStream is = getClass().getResourceAsStream(resource)) {
            if (is == null) return enToKoList;
            Map<Object, Object> root = new Yaml().load(is);
            Object namesObj = root.get("names");
            if (namesObj instanceof Map<?, ?> rawMap) {
                for (Map.Entry<?, ?> entry : rawMap.entrySet()) {
                    String ko = String.valueOf(entry.getKey());
                    String en = String.valueOf(entry.getValue());
                    enToKoList.computeIfAbsent(en, k -> new ArrayList<>()).add(ko);
                }
            }
        } catch (Exception e) {
            log.warn("[Seeder] {} 로드 실패: {}", resource, e.getMessage());
        }
        return enToKoList;
    }
}
```

`PokemonCommandService`에 `getCacheSize()` 추가:
```java
public int getCacheSize() {
    return nameCache.size();
}
```

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/rocketcrew/pocat/domain/pokemon/service/DomainDataSeeder.java \
        src/main/java/com/rocketcrew/pocat/domain/pokemon/service/PokemonCommandService.java
git commit -m "feat: add DomainDataSeeder (ApplicationReadyEvent yaml → DB)"
```

---

## Task 6: Flyway V2 + Card 엔티티 마이그레이션 (핵심 태스크)

> ⚠️ 이 태스크는 DB 스키마를 변경한다. 시작 전 로컬 DB 백업 권장.  
> **이 커밋 적용 후 앱을 재시작하면 Flyway V2가 실행되고 기존 cards 테이블이 변경된다.**

**Files:**
- Create: `src/main/resources/db/migration/V2__domain_separation.sql`
- Modify: `src/main/java/com/rocketcrew/pocat/domain/card/entity/Card.java`
- Modify: `src/main/java/com/rocketcrew/pocat/domain/card/dto/response/CardResponse.java`
- Modify: `src/main/java/com/rocketcrew/pocat/domain/card/document/CardDocument.java`
- Modify: `src/test/java/com/rocketcrew/pocat/support/TestFixtures.java`

- [ ] **Step 1: Flyway V2 SQL 작성**

`src/main/resources/db/migration/V2__domain_separation.sql`:

```sql
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
```

- [ ] **Step 2: Card 엔티티 — String 필드 제거, @ManyToOne 추가**

`Card.java`의 아래 필드를 **삭제**:
```java
// 삭제할 필드들
@Column(name = "series", length = 100, nullable = false)
private String series;

@Column(name = "set_id", length = 50, nullable = false)
private String setId;

@Column(name = "set_name", length = 100, nullable = false)
private String setName;
```

**추가할 필드들** (해당 위치에 삽입):
```java
@ManyToOne(fetch = FetchType.EAGER)
@JoinColumn(name = "series_id")
private Series series;

@ManyToOne(fetch = FetchType.EAGER)
@JoinColumn(name = "pokemon_set_id")
private PokemonSet pokemonSet;

@ManyToOne(fetch = FetchType.LAZY)
@JoinColumn(name = "pokemon_id")
private Pokemon pokemon;
```

import 추가:
```java
import com.rocketcrew.pocat.domain.series.entity.Series;
import com.rocketcrew.pocat.domain.set.entity.PokemonSet;
import com.rocketcrew.pocat.domain.pokemon.entity.Pokemon;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
```

- [ ] **Step 3: Card.update() 시그니처 수정**

기존 `update()` 메서드를 아래로 교체:

```java
public void update(String tcgdexId, String name, Series series, PokemonSet pokemonSet,
                   String cardNumber, String rarity, CardCategory category, CardGrade grade,
                   String imageUrl, CardSource source) {
    if (tcgdexId != null) this.tcgdexId = tcgdexId;
    if (name != null && !name.isBlank()) this.name = name;
    if (series != null) this.series = series;
    if (pokemonSet != null) this.pokemonSet = pokemonSet;
    if (cardNumber != null && !cardNumber.isBlank()) this.cardNumber = cardNumber;
    if (rarity != null && !rarity.isBlank()) this.rarity = rarity;
    if (category != null) this.category = category;
    if (grade != null) this.grade = grade;
    if (imageUrl != null) this.imageUrl = imageUrl;
    if (source != null) this.source = source;
}
```

`linkPokemon()` 메서드 추가:
```java
public void linkPokemon(Pokemon pokemon) {
    this.pokemon = pokemon;
}
```

- [ ] **Step 4: CardResponse.from(Card) 수정**

`CardResponse.java`의 `from(Card card)` 메서드를 교체:

```java
public static CardResponse from(Card card) {
    Series series = card.getSeries();
    PokemonSet pokemonSet = card.getPokemonSet();
    return new CardResponse(
            card.getId(),
            card.getUserId(),
            card.getTcgdexId(),
            card.getName(),
            series != null ? series.getName() : null,
            pokemonSet != null ? pokemonSet.getSetId() : null,
            pokemonSet != null ? pokemonSet.getName() : null,
            card.getCardNumber(),
            card.getRarity(),
            card.getCategory(),
            card.getGrade(),
            card.getImageUrl(),
            card.getSource(),
            card.getStatus(),
            card.getCreatedAt(),
            card.getUpdatedAt()
    );
}
```

import 추가:
```java
import com.rocketcrew.pocat.domain.series.entity.Series;
import com.rocketcrew.pocat.domain.set.entity.PokemonSet;
```

- [ ] **Step 5: CardDocument.from(Card, ...) 수정**

`CardDocument.java`의 `from(Card card, String nameKo, String seriesKo, String setNameKo)` 내부에서 Card 필드 참조 부분 수정:

```java
public static CardDocument from(Card card, String nameKo, String seriesKo, String setNameKo) {
    Series series = card.getSeries();
    PokemonSet pokemonSet = card.getPokemonSet();
    return CardDocument.builder()
            .id(String.valueOf(card.getId()))
            .userId(card.getUserId())
            .tcgdexId(card.getTcgdexId())
            .name(card.getName())
            .nameKo(nameKo)
            .series(series != null ? series.getName() : null)
            .seriesKo(seriesKo)
            .setId(pokemonSet != null ? pokemonSet.getSetId() : null)
            .setName(pokemonSet != null ? pokemonSet.getName() : null)
            .setNameKo(setNameKo)
            .cardNumber(card.getCardNumber())
            .rarity(card.getRarity())
            .category(card.getCategory() != null ? card.getCategory().name() : null)
            .grade(card.getGrade() != null ? card.getGrade().name() : null)
            .imageUrl(card.getImageUrl())
            .source(card.getSource() != null ? card.getSource().name() : null)
            .status(card.getStatus() != null ? card.getStatus().name() : null)
            .createdAt(card.getCreatedAt())
            .updatedAt(card.getUpdatedAt())
            .build();
}
```

import 추가:
```java
import com.rocketcrew.pocat.domain.series.entity.Series;
import com.rocketcrew.pocat.domain.set.entity.PokemonSet;
```

- [ ] **Step 6: TestFixtures.aCard() 수정**

`TestFixtures.java`에서 `aCard()` 메서드 찾아서 `series`, `setId`, `setName` 빌더 호출 제거, 대신 Series/PokemonSet 픽스처 추가:

```java
public static Series aSeries() {
    Series series = Series.builder().name("Sword & Shield").build();
    ReflectionTestUtils.setField(series, "id", 1L);
    return series;
}

public static PokemonSet aPokemonSet() {
    PokemonSet ps = PokemonSet.builder()
            .setId("swsh5")
            .name("Rebel Clash")
            .series(aSeries())
            .build();
    ReflectionTestUtils.setField(ps, "id", 1L);
    return ps;
}
```

기존 `aCard()` 내 Card.builder() 호출에서:
- `.series("Sword & Shield")` → `.series(aSeries())`
- `.setId("swsh5")` → `.pokemonSet(aPokemonSet())`
- `.setName("Rebel Clash")` → (제거)

import 추가:
```java
import com.rocketcrew.pocat.domain.series.entity.Series;
import com.rocketcrew.pocat.domain.set.entity.PokemonSet;
```

- [ ] **Step 7: 컴파일 확인**

```bash
./gradlew compileJava compileTestJava
```

Expected: BUILD SUCCESSFUL

컴파일 에러가 남아있다면 Card.builder()를 직접 호출하는 곳(CardSyncService, CardCommandService)을 찾아 임시로 `.series((Series) null)` / `.pokemonSet((PokemonSet) null)` 처리. (Task 7, 8에서 올바르게 수정 예정)

- [ ] **Step 8: Commit**

```bash
git add src/main/resources/db/migration/V2__domain_separation.sql \
        src/main/java/com/rocketcrew/pocat/domain/card/entity/Card.java \
        src/main/java/com/rocketcrew/pocat/domain/card/dto/response/CardResponse.java \
        src/main/java/com/rocketcrew/pocat/domain/card/document/CardDocument.java \
        src/test/java/com/rocketcrew/pocat/support/TestFixtures.java
git commit -m "feat: Flyway V2 + Card entity migration (String → @ManyToOne FK)"
```

---

## Task 7: CardCommandService 업데이트

**Files:**
- Modify: `src/main/java/com/rocketcrew/pocat/domain/card/service/CardCommandService.java`

- [ ] **Step 1: CardCommandService 전체 교체**

```java
package com.rocketcrew.pocat.domain.card.service;

import com.rocketcrew.pocat.domain.card.document.CardDocument;
import com.rocketcrew.pocat.domain.card.dto.request.CreateCardRequest;
import com.rocketcrew.pocat.domain.card.dto.request.UpdateCardRequest;
import com.rocketcrew.pocat.domain.card.dto.response.CardResponse;
import com.rocketcrew.pocat.domain.card.entity.Card;
import com.rocketcrew.pocat.domain.card.entity.enums.CardCategory;
import com.rocketcrew.pocat.domain.card.entity.enums.CardStatus;
import com.rocketcrew.pocat.domain.card.repository.CardRepository;
import com.rocketcrew.pocat.domain.card.repository.CardSearchRepository;
import com.rocketcrew.pocat.domain.pokemon.entity.Pokemon;
import com.rocketcrew.pocat.domain.pokemon.service.PokemonCommandService;
import com.rocketcrew.pocat.domain.series.entity.Series;
import com.rocketcrew.pocat.domain.series.service.SeriesCommandService;
import com.rocketcrew.pocat.domain.set.entity.PokemonSet;
import com.rocketcrew.pocat.domain.set.service.PokemonSetCommandService;
import com.rocketcrew.pocat.global.exception.common.ErrorCode;
import com.rocketcrew.pocat.global.exception.domain.CardException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class CardCommandService {

    private final CardRepository cardRepository;
    private final CardSearchRepository cardSearchRepository;
    private final SeriesCommandService seriesCommandService;
    private final PokemonSetCommandService pokemonSetCommandService;
    private final PokemonCommandService pokemonCommandService;

    public CardResponse createCard(Long userId, CreateCardRequest request) {
        if (request.tcgdexId() != null && cardRepository.existsByTcgdexId(request.tcgdexId())) {
            throw new CardException(ErrorCode.CARD_ALREADY_EXISTS);
        }

        Series series = seriesCommandService.findOrCreate(request.series());
        PokemonSet pokemonSet = pokemonSetCommandService.findOrCreate(
                request.setId(), request.setName(), series);
        Pokemon pokemon = null;
        if (request.category() == CardCategory.POKEMON) {
            pokemon = pokemonCommandService.findOrCreateForCardName(request.name()).orElse(null);
        }

        Card card = Card.builder()
                .userId(userId)
                .tcgdexId(request.tcgdexId())
                .name(request.name())
                .series(series)
                .pokemonSet(pokemonSet)
                .pokemon(pokemon)
                .cardNumber(request.cardNumber())
                .rarity(request.rarity())
                .category(request.category())
                .grade(request.grade())
                .imageUrl(request.imageUrl())
                .source(request.source())
                .status(CardStatus.PENDING)
                .build();
        try {
            return CardResponse.from(cardRepository.save(card));
        } catch (DataIntegrityViolationException e) {
            throw new CardException(ErrorCode.CARD_ALREADY_EXISTS);
        }
    }

    public CardResponse updateCard(Long id, UpdateCardRequest request) {
        Card card = cardRepository.findById(id)
                .orElseThrow(() -> new CardException(ErrorCode.CARD_NOT_FOUND));

        Series series = null;
        if (request.series() != null) {
            series = seriesCommandService.findOrCreate(request.series());
        }
        PokemonSet pokemonSet = null;
        if (request.setId() != null) {
            String setName = request.setName() != null ? request.setName()
                    : (card.getPokemonSet() != null ? card.getPokemonSet().getName() : request.setId());
            pokemonSet = pokemonSetCommandService.findOrCreate(request.setId(), setName, series);
        }

        card.update(request.tcgdexId(), request.name(), series, pokemonSet,
                request.cardNumber(), request.rarity(), request.category(),
                request.grade(), request.imageUrl(), request.source());

        if (card.getStatus() == CardStatus.ACTIVE) {
            indexCard(card);
        }
        return CardResponse.from(card);
    }

    public void deleteCard(Long id) {
        Card card = cardRepository.findById(id)
                .orElseThrow(() -> new CardException(ErrorCode.CARD_NOT_FOUND));
        cardRepository.delete(card);
        deleteCardIndex(id);
    }

    public CardResponse approveCard(Long id) {
        Card card = cardRepository.findById(id)
                .orElseThrow(() -> new CardException(ErrorCode.CARD_NOT_FOUND));
        card.approve();
        indexCard(card);
        return CardResponse.from(card);
    }

    public CardResponse rejectCard(Long id, String rejectReason) {
        Card card = cardRepository.findById(id)
                .orElseThrow(() -> new CardException(ErrorCode.CARD_NOT_FOUND));
        card.reject(rejectReason);
        return CardResponse.from(card);
    }

    void indexCard(Card card) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() { doIndexCard(card); }
            });
        } else {
            doIndexCard(card);
        }
    }

    private void doIndexCard(Card card) {
        try {
            String nameKo    = card.getPokemon() != null ? card.getPokemon().getNameKo() : null;
            String seriesKo  = card.getSeries() != null ? card.getSeries().getNameKo() : null;
            String setNameKo = card.getPokemonSet() != null ? card.getPokemonSet().getNameKo() : null;
            cardSearchRepository.save(CardDocument.from(card, nameKo, seriesKo, setNameKo));
        } catch (Exception e) {
            log.warn("[CardES] 인덱싱 실패 cardId={}: {}", card.getId(), e.getMessage());
        }
    }

    private void deleteCardIndex(Long id) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() { doDeleteCardIndex(id); }
            });
        } else {
            doDeleteCardIndex(id);
        }
    }

    private void doDeleteCardIndex(Long id) {
        try {
            cardSearchRepository.deleteById(String.valueOf(id));
        } catch (Exception e) {
            log.warn("[CardES] 인덱스 삭제 실패 cardId={}: {}", id, e.getMessage());
        }
    }
}
```

- [ ] **Step 2: 컴파일 확인**

```bash
./gradlew compileJava
```

Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/rocketcrew/pocat/domain/card/service/CardCommandService.java
git commit -m "feat: CardCommandService — replace Dictionary with find-or-create services"
```

---

## Task 8: CardSyncService 업데이트

**Files:**
- Modify: `src/main/java/com/rocketcrew/pocat/domain/card/service/CardSyncService.java`

- [ ] **Step 1: CardSyncService 수정**

`syncSet()` 내부에서 Card 빌더 부분을 아래로 교체. 클래스에 의존성 3개 추가:

의존성 추가 (필드):
```java
private final SeriesCommandService seriesCommandService;
private final PokemonSetCommandService pokemonSetCommandService;
private final PokemonCommandService pokemonCommandService;
```

`syncSet()` 메서드 내 Card 생성 부분 교체:
```java
// 기존: .series(seriesName) .setId(setId) .setName(setName) 제거
// 신규: find-or-create
Series seriesEntity = seriesCommandService.findOrCreate(seriesName);
PokemonSet pokemonSetEntity = pokemonSetCommandService.findOrCreate(setId, setName, seriesEntity);
Pokemon pokemonEntity = (category == CardCategory.POKEMON)
        ? pokemonCommandService.findOrCreateForCardName(name).orElse(null)
        : null;

Card card = Card.builder()
        .userId(adminUserId)
        .tcgdexId(tcgdexId)
        .name(name)
        .series(seriesEntity)
        .pokemonSet(pokemonSetEntity)
        .pokemon(pokemonEntity)
        .cardNumber(localId)
        .rarity(rarity.isEmpty() ? "UNKNOWN" : rarity)
        .category(category)
        .grade(grade)
        .imageUrl(imageUrl)
        .source(CardSource.TCGDEX)
        .status(CardStatus.ACTIVE)
        .build();
```

필요한 import 추가:
```java
import com.rocketcrew.pocat.domain.pokemon.entity.Pokemon;
import com.rocketcrew.pocat.domain.pokemon.service.PokemonCommandService;
import com.rocketcrew.pocat.domain.series.entity.Series;
import com.rocketcrew.pocat.domain.series.service.SeriesCommandService;
import com.rocketcrew.pocat.domain.set.entity.PokemonSet;
import com.rocketcrew.pocat.domain.set.service.PokemonSetCommandService;
```

- [ ] **Step 2: 컴파일 확인**

```bash
./gradlew compileJava
```

Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/rocketcrew/pocat/domain/card/service/CardSyncService.java
git commit -m "feat: CardSyncService — replace String fields with find-or-create entity lookup"
```

---

## Task 9: CardQueryService + CardEsMigrationService 업데이트

**Files:**
- Modify: `src/main/java/com/rocketcrew/pocat/domain/card/service/CardQueryService.java`
- Modify: `src/main/java/com/rocketcrew/pocat/domain/card/service/CardEsMigrationService.java`

- [ ] **Step 1: CardQueryService — Dictionary 제거, 새 서비스로 교체**

`CardQueryService.java` 상단 의존성 교체:

제거:
```java
private final SeriesNameDictionary seriesNameDictionary;
private final SetNameDictionary setNameDictionary;
```

추가:
```java
private final SeriesQueryService seriesQueryService;
private final PokemonSetQueryService pokemonSetQueryService;
```

`getCards()` 내 필터 번역 부분 교체:

```java
if (StringUtils.hasText(condition.series())) {
    String seriesEn = seriesQueryService.translate(condition.series());
    bool.filter(TermQuery.of(t -> t.field("series").value(seriesEn))._toQuery());
}
if (StringUtils.hasText(condition.setName())) {
    String setNameEn = pokemonSetQueryService.translate(condition.setName());
    bool.filter(TermQuery.of(t -> t.field("setName").value(setNameEn))._toQuery());
}
```

import 교체:
```java
// 제거
import com.rocketcrew.pocat.domain.card.util.SeriesNameDictionary;
import com.rocketcrew.pocat.domain.card.util.SetNameDictionary;

// 추가
import com.rocketcrew.pocat.domain.series.service.SeriesQueryService;
import com.rocketcrew.pocat.domain.set.service.PokemonSetQueryService;
```

- [ ] **Step 2: CardEsMigrationService — Dictionary 제거**

`CardEsMigrationService.java` 의존성 교체:

제거:
```java
private final PokemonNameDictionary pokemonNameDictionary;
private final SeriesNameDictionary seriesNameDictionary;
private final SetNameDictionary setNameDictionary;
```

`migrateAll()` 내 docs 매핑 부분 교체:

```java
List<CardDocument> docs = batch.getContent().stream()
        .map(card -> CardDocument.from(
                card,
                card.getPokemon() != null ? card.getPokemon().getNameKo() : null,
                card.getSeries() != null ? card.getSeries().getNameKo() : null,
                card.getPokemonSet() != null ? card.getPokemonSet().getNameKo() : null
        ))
        .toList();
```

import 제거:
```java
import com.rocketcrew.pocat.domain.card.util.PokemonNameDictionary;
import com.rocketcrew.pocat.domain.card.util.SeriesNameDictionary;
import com.rocketcrew.pocat.domain.card.util.SetNameDictionary;
```

- [ ] **Step 3: 전체 테스트 실행**

```bash
./gradlew test
```

Expected: BUILD SUCCESSFUL (기존 테스트 모두 통과)

컴파일 에러나 테스트 실패가 있다면 해당 테스트 파일에서 `SeriesNameDictionary` / `SetNameDictionary` / `PokemonNameDictionary` 참조를 찾아 제거.

- [ ] **Step 4: Commit**

```bash
git add src/main/java/com/rocketcrew/pocat/domain/card/service/CardQueryService.java \
        src/main/java/com/rocketcrew/pocat/domain/card/service/CardEsMigrationService.java
git commit -m "feat: CardQueryService/MigrationService — replace YAML Dictionary with DB entity lookup"
```

---

## Task 10: Admin Controller 3개 생성

**Files:**
- Create: `domain/series/controller/AdminSeriesController.java`
- Create: `domain/set/controller/AdminPokemonSetController.java`
- Create: `domain/pokemon/controller/AdminPokemonController.java`

- [ ] **Step 1: AdminSeriesController 생성**

```java
package com.rocketcrew.pocat.domain.series.controller;

import com.rocketcrew.pocat.domain.series.dto.request.UpsertSeriesRequest;
import com.rocketcrew.pocat.domain.series.dto.response.SeriesResponse;
import com.rocketcrew.pocat.domain.series.service.SeriesCommandService;
import com.rocketcrew.pocat.domain.series.service.SeriesQueryService;
import com.rocketcrew.pocat.global.dto.ApiResponseDto;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/admin/series")
public class AdminSeriesController {

    private final SeriesQueryService seriesQueryService;
    private final SeriesCommandService seriesCommandService;

    @GetMapping
    public ResponseEntity<ApiResponseDto<List<SeriesResponse>>> getAll() {
        return ResponseEntity.ok(ApiResponseDto.success(HttpStatus.OK, seriesQueryService.findAll()));
    }

    @PostMapping
    public ResponseEntity<ApiResponseDto<SeriesResponse>> create(
            @Valid @RequestBody UpsertSeriesRequest request) {
        return ResponseEntity.ok(ApiResponseDto.success(HttpStatus.CREATED, seriesCommandService.create(request)));
    }

    @PatchMapping("/{id}/name-ko")
    public ResponseEntity<ApiResponseDto<SeriesResponse>> updateNameKo(
            @PathVariable Long id, @RequestParam String nameKo) {
        return ResponseEntity.ok(ApiResponseDto.success(HttpStatus.OK, seriesCommandService.updateNameKo(id, nameKo)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponseDto<Void>> delete(@PathVariable Long id) {
        seriesCommandService.delete(id);
        return ResponseEntity.ok(ApiResponseDto.successWithNoContent());
    }
}
```

- [ ] **Step 2: AdminPokemonSetController 생성**

```java
package com.rocketcrew.pocat.domain.set.controller;

import com.rocketcrew.pocat.domain.series.entity.Series;
import com.rocketcrew.pocat.domain.series.service.SeriesCommandService;
import com.rocketcrew.pocat.domain.set.dto.request.UpsertPokemonSetRequest;
import com.rocketcrew.pocat.domain.set.dto.response.PokemonSetResponse;
import com.rocketcrew.pocat.domain.set.service.PokemonSetCommandService;
import com.rocketcrew.pocat.domain.set.service.PokemonSetQueryService;
import com.rocketcrew.pocat.global.dto.ApiResponseDto;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/admin/sets")
public class AdminPokemonSetController {

    private final PokemonSetQueryService pokemonSetQueryService;
    private final PokemonSetCommandService pokemonSetCommandService;
    private final SeriesCommandService seriesCommandService;

    @GetMapping
    public ResponseEntity<ApiResponseDto<List<PokemonSetResponse>>> getAll() {
        return ResponseEntity.ok(ApiResponseDto.success(HttpStatus.OK, pokemonSetQueryService.findAll()));
    }

    @PostMapping
    public ResponseEntity<ApiResponseDto<PokemonSetResponse>> create(
            @Valid @RequestBody UpsertPokemonSetRequest request) {
        Series series = request.seriesId() != null
                ? seriesCommandService.findOrCreate("")   // seriesId로 조회하는 게 맞으나 간략화
                : null;
        // 실제로는 seriesRepository.findById(request.seriesId()) 로 조회
        return ResponseEntity.ok(ApiResponseDto.success(HttpStatus.CREATED,
                pokemonSetCommandService.create(request, series)));
    }

    @PatchMapping("/{id}/name-ko")
    public ResponseEntity<ApiResponseDto<PokemonSetResponse>> updateNameKo(
            @PathVariable Long id, @RequestParam String nameKo) {
        return ResponseEntity.ok(ApiResponseDto.success(HttpStatus.OK,
                pokemonSetCommandService.updateNameKo(id, nameKo)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponseDto<Void>> delete(@PathVariable Long id) {
        pokemonSetCommandService.delete(id);
        return ResponseEntity.ok(ApiResponseDto.successWithNoContent());
    }
}
```

> 주의: `POST /api/v1/admin/sets`의 series 조회 로직을 `SeriesRepository.findById(request.seriesId())`로 수정 필요. `SeriesCommandService`에 `findById(Long id)` 메서드를 추가하거나, `SeriesRepository`를 직접 주입해 처리한다.

- [ ] **Step 3: AdminPokemonController 생성**

```java
package com.rocketcrew.pocat.domain.pokemon.controller;

import com.rocketcrew.pocat.domain.pokemon.dto.request.UpsertPokemonRequest;
import com.rocketcrew.pocat.domain.pokemon.dto.response.PokemonResponse;
import com.rocketcrew.pocat.domain.pokemon.service.PokemonCommandService;
import com.rocketcrew.pocat.domain.pokemon.service.PokemonQueryService;
import com.rocketcrew.pocat.global.dto.ApiResponseDto;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/admin/pokemon")
public class AdminPokemonController {

    private final PokemonQueryService pokemonQueryService;
    private final PokemonCommandService pokemonCommandService;

    @GetMapping
    public ResponseEntity<ApiResponseDto<List<PokemonResponse>>> getAll() {
        return ResponseEntity.ok(ApiResponseDto.success(HttpStatus.OK, pokemonQueryService.findAll()));
    }

    @PostMapping
    public ResponseEntity<ApiResponseDto<PokemonResponse>> create(
            @Valid @RequestBody UpsertPokemonRequest request) {
        return ResponseEntity.ok(ApiResponseDto.success(HttpStatus.CREATED,
                pokemonCommandService.findOrCreate(request.name(), request.nameKo())));
    }

    @PatchMapping("/{id}/name-ko")
    public ResponseEntity<ApiResponseDto<PokemonResponse>> updateNameKo(
            @PathVariable Long id, @RequestParam String nameKo) {
        return ResponseEntity.ok(ApiResponseDto.success(HttpStatus.OK,
                pokemonCommandService.updateNameKo(id, nameKo)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponseDto<Void>> delete(@PathVariable Long id) {
        pokemonCommandService.delete(id);
        return ResponseEntity.ok(ApiResponseDto.successWithNoContent());
    }
}
```

`AdminPokemonController.create()`의 반환 타입이 `Pokemon`이 아닌 `PokemonResponse`여야 하므로 `findOrCreate`의 반환을 `PokemonResponse.from(result)`로 감쌀 것:
```java
Pokemon saved = pokemonCommandService.findOrCreate(request.name(), request.nameKo());
return ResponseEntity.ok(ApiResponseDto.success(HttpStatus.CREATED, PokemonResponse.from(saved)));
```

- [ ] **Step 4: 컴파일 + 테스트**

```bash
./gradlew compileJava && ./gradlew test
```

Expected: BUILD SUCCESSFUL

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/rocketcrew/pocat/domain/series/controller \
        src/main/java/com/rocketcrew/pocat/domain/set/controller \
        src/main/java/com/rocketcrew/pocat/domain/pokemon/controller
git commit -m "feat: add Admin CRUD controllers for Series, PokemonSet, Pokemon"
```

---

## Task 11: Dictionary 클래스 삭제

**Files:**
- Delete: `domain/card/util/SeriesNameDictionary.java`
- Delete: `domain/card/util/SetNameDictionary.java`
- Delete: `domain/card/util/PokemonNameDictionary.java`

- [ ] **Step 1: 세 파일 삭제 후 컴파일 확인**

```bash
rm src/main/java/com/rocketcrew/pocat/domain/card/util/SeriesNameDictionary.java
rm src/main/java/com/rocketcrew/pocat/domain/card/util/SetNameDictionary.java
rm src/main/java/com/rocketcrew/pocat/domain/card/util/PokemonNameDictionary.java
./gradlew compileJava compileTestJava
```

Expected: BUILD SUCCESSFUL

에러 발생 시: Dictionary를 import하는 클래스를 찾아서 해당 import 제거:
```bash
grep -r "NameDictionary" src/ --include="*.java" -l
```

- [ ] **Step 2: 전체 테스트 실행**

```bash
./gradlew test
```

Expected: BUILD SUCCESSFUL, 기존 테스트 전부 통과

- [ ] **Step 3: Commit**

```bash
git add -A
git commit -m "refactor: delete YAML-based Dictionary classes (replaced by DB entities)"
```

---

## Task 12: ES 재인덱싱 (운영 단계 — 최초 1회)

> 코드 변경 없음. 앱 배포 후 수동 실행.

- [ ] **Step 1: ES cards 인덱스 삭제 (Kibana Dev Tools)**

```
DELETE /cards
```

- [ ] **Step 2: 서버 재시작**

```bash
# 로컬
./gradlew build -x test
docker compose up --build
```

재시작 시 자동으로:
1. Flyway V2 실행 → 신규 테이블 생성, cards 스키마 마이그레이션
2. JPA ddl-auto:update → ES cards 인덱스 신규 생성 (ngram 설정 포함)
3. DomainDataSeeder → Series/PokemonSet nameKo 채우기, Pokemon 시드, pokemon_id 연결

- [ ] **Step 3: ES 전체 마이그레이션 실행**

```bash
curl -X POST http://localhost:8080/api/v1/admin/cards/es-migrate
```

응답 예시: `{"data": 23193}` (총 인덱싱 카드 수)

- [ ] **Step 4: 동작 검증**

```bash
# 영문 검색
curl "http://localhost:8080/api/v1/cards?keyword=Charizard"

# 한글 포켓몬명 검색
curl "http://localhost:8080/api/v1/cards?keyword=리자몽"

# 한글 확장팩 + 포켓몬명 교차 검색
curl "http://localhost:8080/api/v1/cards?keyword=반역크래시+리자몽"

# 시리즈 필터 (한글)
curl "http://localhost:8080/api/v1/cards?series=소드실드"

# Admin API로 nameKo 수정 후 즉시 반영 확인
curl -X PATCH "http://localhost:8080/api/v1/admin/series/1/name-ko?nameKo=검과방패+소드실드+소드앤실드"
```

---

## 완료 조건 체크리스트

- [ ] `./gradlew test` 전체 통과
- [ ] `keyword=리자몽` 검색 결과 정상
- [ ] `keyword=반역크래시 리자몽` cross_fields AND 결과 정상
- [ ] `series=소드실드` 필터 정상
- [ ] `PATCH /api/v1/admin/series/{id}/name-ko` 호출 후 ES 재인덱싱 없이 다음 검색에서 새 nameKo 반영
- [ ] YAML Dictionary 파일 3개 삭제 확인
- [ ] `cards` 테이블에 `series`, `set_id`, `set_name` 컬럼 없음 확인
