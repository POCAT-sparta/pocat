# ADR-001: 로컬 개발 환경 Docker Compose 인프라 구성

| 항목 | 내용 |
|------|------|
| **날짜** | 2026-05-21 |
| **상태** | Accepted |
| **결정자** | 개발팀 전체 |

---

## 맥락 (Context)

POCAT 프로젝트는 Spring Boot 백엔드, MySQL, Redis, Kafka(3-브로커), WebSocket, Prometheus/Grafana 모니터링 스택을 포함하는 다중 컴포넌트 애플리케이션이다.

로컬 개발 시 팀원마다 OS 및 설치 환경이 상이하여 다음 문제가 반복적으로 발생하였다:

- 로컬에 설치된 MySQL(기본 포트 3306)과의 포트 충돌
- Kafka 브로커 설정 복잡성으로 인한 환경 재현 불일치
- 모니터링 스택(Prometheus, Grafana) 수동 설치 비용
- `.env` 시크릿과 서비스 간 네트워크 호스트명 관리의 혼선

팀은 단일 `docker compose` 명령으로 전체 로컬 환경을 일관되게 재현할 수 있는 표준 인프라 구성이 필요하다고 판단하였다.

---

## 결정 (Decision)

### 1. 전체 서비스 구성

`docker-compose.yml` 하나로 아래 서비스를 정의한다.

| 서비스 | 이미지 | 포트 매핑 | 프로파일 |
|--------|--------|-----------|---------|
| `backend` | 로컬 bootJar 기반 커스텀 이미지 | 8080:8080 | (기본) |
| `db` | mysql:8.0 | 3307:3306 | (기본) |
| `redis` | redis:7.2-alpine | 6379:6379 | (기본) |
| `kafka-1/2/3` | bitnami/kafka:3.7.0 | 9092–9094 | `kafka` |
| `kafka-ui` | provectuslabs/kafka-ui | 8989:8080 | `kafka` |
| `prometheus` | prom/prometheus | 9090:9090 | (기본) |
| `grafana` | grafana/grafana | 3000:3000 | (기본) |

### 2. Kafka 이미지: bitnami/kafka:3.7.0 선택

**대안 검토**: `confluentinc/cp-kafka` vs `bitnami/kafka`

- `bitnami/kafka`는 이미지 크기가 더 작고, 팀이 이전 프로젝트에서 운용 경험을 보유함
- KRaft 모드(ZooKeeper 불필요) 기본 지원으로 브로커 3개만으로 클러스터 구성 가능
- Confluent 이미지는 라이선스 확인 부담이 있으며, 추가 Confluent 전용 설정이 필요함

**결정**: `bitnami/kafka:3.7.0` + KRaft 3-브로커 클러스터

### 3. Kafka compose profiles 격리

Kafka 3-브로커 + UI는 기본 부팅 시 제외하고 `profiles: [kafka]`로 격리한다.

**이유**:
- Kafka 클러스터는 메모리 소모가 크며(브로커당 ~512MB), 메시징 기능 개발이 필요 없는 작업 시 불필요한 자원 낭비
- `docker compose up` (기본) → backend + db + redis + prometheus + grafana만 기동
- `docker compose --profile kafka up` → Kafka 클러스터 및 UI 추가 기동

### 4. MySQL 포트 3307 매핑

호스트 포트를 `3307:3306`으로 매핑한다.

**이유**: 팀원 로컬 환경에 MySQL이 기본 포트(3306)로 설치되어 있는 경우가 많아 충돌 방지. 컨테이너 내부 통신은 여전히 3306 사용.

### 5. env_file + environment 오버라이드 패턴

```yaml
env_file:
  - .env
environment:
  DB_URL: jdbc:mysql://db:3306/pocat
  REDIS_HOST: redis
```

- `.env`에는 DB 비밀번호, JWT 시크릿, 외부 API 키 등 민감 정보를 보관 (`.gitignore` 등록)
- `environment` 블록에서 `DB_URL`, `REDIS_HOST` 등 서비스 디스커버리 관련 값을 Docker 내부 서비스명으로 명시적으로 덮어씀
- 이로써 로컬 실행(localhost 기반)과 Docker 실행(서비스명 기반) 환경 간 전환을 `.env` 하나로 제어 가능

### 6. 백엔드 컨테이너화

`bootJar` 태스크를 호스트에서 빌드한 뒤, compose가 Dockerfile로 이미지를 빌드한다.

**이유**: 컨테이너 내부에서 Gradle 빌드를 수행하면 의존성 캐시 관리가 복잡해지고 빌드 시간이 길어짐. 호스트 빌드 후 JAR만 컨테이너에 복사하는 방식으로 빌드 속도 최적화.

### 7. Prometheus/Grafana + JVM 대시보드

`micrometer-registry-prometheus`를 `build.gradle`에 추가하고, Spring Actuator `/actuator/prometheus` 엔드포인트를 Prometheus가 스크레이핑하도록 구성한다. Grafana에는 JVM(Micrometer) 공식 대시보드(ID: 4701)를 프로비저닝한다.

---

## 결과 (Consequences)

### 긍정적 영향

- **환경 재현성**: `docker compose up` 한 줄로 모든 팀원이 동일한 인프라 환경 확보
- **온보딩 속도 향상**: 신규 팀원이 별도 미들웨어 설치 없이 즉시 개발 착수 가능
- **포트 충돌 제거**: MySQL 3307 매핑으로 로컬 설치 DB와 공존 가능
- **자원 효율**: Kafka 프로파일 분리로 불필요한 메모리 사용 방지
- **시크릿 관리 명확화**: `.env`와 서비스명 오버라이드 패턴으로 개발/컨테이너 환경 분리
- **관측성 내장**: Prometheus + Grafana + JVM 대시보드로 로컬에서도 메트릭 확인 가능

### 부정적 영향 / 주의사항

- **백엔드 이미지 재빌드 필요**: 코드 변경 시 `./gradlew bootJar` 후 `docker compose build backend` 수행 필요 — 핫 리로드 미지원
- **Kafka 초기화 시간**: KRaft 클러스터 기동 시 브로커 간 메타데이터 동기화로 약 15~30초 대기 필요
- **포트 충돌 가능성(Kafka)**: 호스트 9092–9094 포트가 다른 로컬 프로세스와 충돌할 가능성 존재 — 팀원 로컬 환경 사전 확인 필요
- **Docker 리소스 설정**: 전체 스택 기동 시 최소 8GB RAM 권장 (Kafka 프로파일 포함 시 12GB 이상 권장)
- **`.env` 파일 공유 방법 별도 필요**: `.env`는 Git에서 제외되므로 팀 내 시크릿 공유 채널(예: 팀 노션, Vault) 필요

---

## 관련 문서

- `docker-compose.yml` — 루트 디렉토리
- `Dockerfile` — 백엔드 이미지 빌드 정의
- `.env.example` — 환경변수 템플릿 (Git 추적)
- `docs/policy/SKILL_STACK.md` — 기술 스택 전체 목록
