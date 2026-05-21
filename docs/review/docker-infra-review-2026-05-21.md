# 코드 리뷰: Docker Compose 인프라 구성

**날짜:** 2026-05-21
**브랜치:** `feat/composeset/#82`
**리뷰어:** REVIEW 에이전트
**범위:** docker-compose.yml, Dockerfile, scripts/start.sh, scripts/stop.sh, infra/prometheus/prometheus.yml, infra/grafana/ 프로비저닝

---

## 요약

Issue #82 Docker Compose 인프라 구성 작업입니다. backend, db, redis, kafka × 3, kafka-ui, prometheus, grafana 총 9개 서비스로 구성됩니다. 발견된 모든 조치 사항이 커밋 `8c5533f`에서 해결되었으며, 남은 블로커는 없습니다.

---

## 발견 사항

### 1. docker-compose.yml — DB 헬스체크 비밀번호 노출 (CRITICAL, 수정 완료)

**파일:** `docker-compose.yml` — `db` 서비스 `healthcheck`
**발견 사항:** 헬스체크 명령에서 `${DB_PASSWORD}`를 사용하면 Compose가 값을 호스트에서 먼저 치환하여 결과 문자열이 컨테이너 메타데이터에 평문으로 저장됩니다. `docker inspect pocat-db` 실행 시 비밀번호가 노출됩니다.
**해결 방법:** CMD-SHELL 형식으로 변경하고 `$$MYSQL_ROOT_PASSWORD`를 사용하여 변수 치환이 컨테이너 내부에서 일어나도록 수정했습니다. 호스트 레벨에서는 비밀번호가 문자열로 남지 않습니다.

---

### 2. docker-compose.yml — Kafka 프로파일 미활성 시 DNS 오류 (HIGH, 수정 완료)

**파일:** `docker-compose.yml` — `backend` 서비스 환경 변수
**발견 사항:** `SPRING_KAFKA_BOOTSTRAP_SERVERS`가 Kafka 프로파일 활성 여부와 무관하게 항상 주입됩니다. Kafka 컨테이너가 준비되기 전이나 Kafka를 사용하지 않는 실행 모드에서 Spring Boot 기동 시 DNS 해석 실패가 발생할 수 있습니다.
**해결 방법:** `SPRING_KAFKA_ADMIN_FAIL_FAST: "false"` 환경 변수를 추가하여 Kafka 접속 실패가 애플리케이션 시작을 중단시키지 않도록 처리했습니다.

---

### 3. docker-compose.yml — Grafana 익명 인증 및 전체 인터페이스 바인딩 (HIGH, 수정 완료)

**파일:** `docker-compose.yml` — `grafana` 서비스 포트 바인딩 및 환경 변수
**발견 사항:** Grafana 익명 접근이 활성화된 상태에서 포트가 모든 인터페이스(`0.0.0.0`)에 바인딩되어 있었습니다. 같은 네트워크의 모든 프로세스가 인증 없이 대시보드에 접근할 수 있었습니다.
**해결 방법:** 모든 서비스 포트를 `127.0.0.1`로 바인딩하도록 수정했습니다.

---

### 4. infra/prometheus/prometheus.yml — `depends_on` 헬스 조건 누락 (MEDIUM, 수정 완료)

**파일:** `docker-compose.yml` — `prometheus` 서비스 `depends_on`
**발견 사항:** `depends_on: backend`에 `condition: service_healthy`가 없어, backend 서비스가 완전히 기동되기 전에 Prometheus가 스크레이핑을 시도했습니다. 기동 직후 수집 실패가 반복될 수 있었습니다.
**해결 방법:** `condition: service_healthy`를 추가하여 backend 헬스체크 통과 후 Prometheus가 기동되도록 수정했습니다.

---

### 5. scripts/start.sh — 헬스 폴링 타임아웃 시 오류 코드 미반환 (MEDIUM, 수정 완료)

**파일:** `scripts/start.sh`
**발견 사항:** 헬스 폴링 루프가 타임아웃 시 종료 코드 0을 반환했습니다. CI 파이프라인이나 자동화 스크립트에서 기동 실패를 성공으로 오인할 수 있었습니다.
**해결 방법:** `HEALTHY` 플래그를 도입하고, 타임아웃 시 `exit 1`을 반환하도록 수정했습니다.

---

### 6. scripts/start.sh — `--build` 플래그 무조건 적용 (MEDIUM, 수정 완료)

**파일:** `scripts/start.sh`
**발견 사항:** `docker compose up --build`가 항상 실행되어 코드 변경 여부와 관계없이 매번 전체 이미지를 재빌드했습니다. 불필요한 빌드 시간이 소요되었습니다.
**해결 방법:** `--build` 플래그를 제거했습니다. Docker 레이어 캐시가 변경된 레이어만 선택적으로 재빌드합니다.

---

### 7. Dockerfile — SIGTERM 전파 불가 (MEDIUM, 수정 완료)

**파일:** `Dockerfile` — `ENTRYPOINT`
**발견 사항:** `sh -c` 쉘 형식의 ENTRYPOINT를 사용하면 PID 1이 `sh` 프로세스가 되어 `docker stop` 시 전송되는 SIGTERM이 JVM에 전달되지 않습니다. 그레이스풀 셧다운이 동작하지 않아 강제 종료(SIGKILL)가 발생했습니다.
**해결 방법:** Exec 형식 `ENTRYPOINT ["java", "-jar", "..."]`으로 변경하여 JVM이 PID 1로 직접 실행되어 SIGTERM을 수신합니다.

---

### 8. Dockerfile — JAR 와일드카드 경로 취약 (MEDIUM, 수정 완료)

**파일:** `Dockerfile` — `COPY` 명령
**발견 사항:** `build/libs/*.jar` 와일드카드를 사용하면 빌드 결과에 `-plain.jar` 등 복수의 JAR 파일이 존재할 경우 복사 대상이 모호해집니다. 예상치 못한 JAR이 이미지에 포함될 수 있었습니다.
**해결 방법:** `POCAT-0.0.1-SNAPSHOT.jar` 구체적 파일명으로 변경했습니다.

---

### 9. infra/prometheus/prometheus.yml — 자체 스크레이핑 중복 (LOW, 유지)

**파일:** `infra/prometheus/prometheus.yml`
**발견 사항:** `localhost:9090` 자기 자신에 대한 스크레이핑이 설정되어 있습니다. 기능상 불필요하나 Prometheus 운영 지표 확인 목적으로 활용 가능합니다.
**결정:** 제거 없이 유지합니다. 해롭지 않으며 운영 모니터링에 참고 가치가 있습니다.

---

### 10. .dockerignore — 불필요한 빌드 컨텍스트 포함 (LOW, 수정 완료)

**파일:** `.dockerignore`
**발견 사항:** `src/`와 `build/resources/`가 `.dockerignore`에 명시되지 않아 빌드 컨텍스트에 포함되었습니다. 이미지에는 복사되지 않으나 빌드 컨텍스트 전송 크기가 증가했습니다.
**해결 방법:** `.dockerignore`에 명시적 제외 항목을 추가했습니다.

---

## 건너뛴 항목 / 수용된 항목

| 항목 | 사유 |
|------|------|
| Prometheus 자체 스크레이핑 (`localhost:9090`) | 해롭지 않음; 운영 지표 확인 목적으로 유지 |
| `SPRING_KAFKA_BOOTSTRAP_SERVERS` 조건부 주입 | `FAIL_FAST: false`로 충분히 완화됨; 프로파일 분기는 이번 PR 범위 외 |

---

## 최종 판정

**승인.** 발견된 모든 조치 사항이 해결되었습니다. 로컬 개발 환경 기준으로 인프라 구성이 안전하고 견고한 상태입니다.
