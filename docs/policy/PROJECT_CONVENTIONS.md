# 📖 프로젝트 컨벤션 및 개발 표준 가이드

이 문서는 모든 프로젝트의 코드 컨벤션, 패키지 구조 및 개발 표준을 정의합니다. 모든 개발자는 일관된 코드 품질과 유지보수성을 위해 본 가이드를 준수해야 합니다.

---

## 🏗 1. 기술 스택 및 표준 (Technology Stack)

### 🛠 핵심 기술
- **프레임워크**: Spring Boot 3.x
- **언어**: Java 17
- **빌드 도구**: Gradle
- **데이터베이스**: MySQL (운영), H2 (테스트)
- **캐시/락**: Redis (Redisson)
- **메시징**: Kafka

### 📦 패키지 구조 (도메인 주도 설계 - DDD)
프로젝트는 도메인 중심의 계층형 아키텍처를 따릅니다.

```text
src/main/java/com/example/kcomproject/
├── config/             # 글로벌 설정 (Redis, ShedLock, Web, Async 등)
├── domain/             # 도메인별 패키지
│   ├── [domain_name]/  # 예: order, user, menu
│   │   ├── controller/ # REST 컨트롤러
│   │   ├── entity/     # JPA 엔티티
│   │   ├── repository/ # Spring Data JPA 레포지토리
│   │   ├── service/    # 비즈니스 로직 및 스케줄러
│   │   └── dto/        # 데이터 전송 객체 (DTO)
│   │       ├── request/
│   │       └── response/
└── global/             # 공통 모듈
    ├── dto/            # ApiResponseDto, ErrorDto 등 공통 응답
    ├── exception/      # GlobalExceptionHandler 및 커스텀 예외
    ├── kafka/          # Kafka 공통 설정 및 프로듀서
    └── entity/         # BaseEntity 등 공통 엔티티 속성
```

---

## 🏷 2. 명명 규칙 및 코딩 표준 (Naming & Coding Standards)

### 🏷 명명 규칙
- **클래스 (Classes)**: PascalCase (예: `OrderService`, `PointController`)
- **메서드/변수 (Methods/Variables)**: camelCase (예: `calculateTotal`, `isDeleted`)
- **상수 (Constants)**: UPPER_SNAKE_CASE (예: `MAX_RETRY_COUNT`)
- **DTO**: `[Action][Domain]Request` 또는 `[Domain]Response` 형식 (Record 사용 권장)
- **패키지 (Packages)**: 소문자, 점 구분 (예: `domain.user.entity`)

### 🛠 코딩 표준
1. **Lombok 사용**:
    - `@Getter`, `@NoArgsConstructor`, `@AllArgsConstructor`, `@Builder` 사용을 권장합니다.
    - 엔티티에서는 순환 참조 및 성능 이슈 방지를 위해 `@Data` 사용을 지양합니다.
2. **JPA 엔티티**:
    - `@Entity` 및 `@Table`을 명시합니다.
    - `@Id`와 `@GeneratedValue(strategy = GenerationType.IDENTITY)`를 통해 기본키를 정의합니다.
    - 모든 엔티티는 `BaseEntity`를 상속받아 `createdAt`, `updatedAt`을 공통 관리합니다.
3. **DTO 전략**:
    - Java **Record**를 기본으로 사용하며, 복잡한 객체 생성 시 `@Builder`를 결합합니다.
    - 엔티티는 절대로 컨트롤러 밖으로 노출하지 않으며, 외부 통신은 반드시 DTO를 통합니다.

---

## 🚀 3. 아키텍처 패턴 및 전략 (Patterns & Strategies)

### 🛠 주요 패턴
1. **응답 래핑 (Response Wrapping)**:
    - 모든 API 응답은 `ApiResponseDto<T>`로 래핑하여 성공/실패 여부와 데이터를 일관되게 반환합니다.
2. **예외 처리 (Exception Handling)**:
    - `GlobalExceptionHandler`에서 모든 예외를 캡처하여 `ErrorCode` 기반의 표준화된 에러 응답을 제공합니다.
3. **동시성 제어 (Concurrency Control)**:
    - 포인트 처리 등 데이터 정합성이 중요한 로직에는 **Redisson 분산 락**을 적용합니다.
4. **데이터 일관성 (Data Consistency)**:
    - 외부 시스템(Kafka) 연동 시 **Transactional Outbox Pattern**을 사용하여 메시지 발행의 원자성을 보장합니다.

---

## 🌿 4. 인프라 및 협업 표준 (Infrastructure & Collaboration)

### 🌿 Git 전략 (Git Flow)
- **main**: 프로덕션 배포 브랜치
- **dev**: 통합 개발 브랜치
- **feat/[topic]/#[issue]**: 기능 개발 브랜치
- **fix/[topic]/#[issue]**: 버그 수정 브랜치

### 💬 커밋 컨벤션
`type(#issue): description` 형식을 따릅니다.
- `feat`: 신규 기능 | `fix`: 버그 수정 | `docs`: 문서 수정
- `refactor`: 코드 리팩토링 | `chore`: 빌드/설정 수정

### ⚙️ CI/CD 및 도구
- **GitHub Actions**: 빌드 및 테스트 자동화
- **Docker**: 컨테이너 기반 실행 환경 보장
- **테스트**: JUnit 5 기반의 단위/통합 테스트 작성
