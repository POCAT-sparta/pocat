# IMPLEMENTATION-003: 배치 서버 분리 구현 현황

> ADR 원문: [ADR-003-batch-server-extraction.md](ADR-003-batch-server-extraction.md)
>
> 이 파일은 ADR-003의 **가변적 구현 현황**만 별도 관리한다.
> 아키텍처 결정 배경·근거는 ADR-003 원문 참조.

---

## 구현 현황 (2026-05-24 기준)

| 항목 | 상태 | 비고 |
|------|------|------|
| pocat-batch 레포 생성 | ✅ 완료 | [github.com/POCAT-sparta/pocat-batch](https://github.com/POCAT-sparta/pocat-batch) |
| Spring Batch 5 인프라 구성 | ✅ 완료 | Spring Boot 3.3.18, MySQL BATCH_* 메타테이블 |
| `FreePostRankingJob` Tasklet | ✅ 완료 | Redis ZSet RENAME 패턴, configurable TTL·weight |
| `ViewCountFlushJob` Tasklet | ✅ 완료 | freeView + freeComment 플러시, failedKey 재시도 |
| `BatchScheduler` (`@Scheduled` + `JobLauncher`) | ✅ 완료 | fixedDelay=60s, JobExecution 상태 검증 |
| `FreePostFlushService` (REQUIRES_NEW 트랜잭션) | ✅ 완료 | 0-row UPDATE → log.warn (삭제된 게시글 비재시도) |
| `FreePostRepository` 벌크 업데이트 반환값 | ✅ 완료 | `void` → `int` (영향 행 수 검증) |
| dotenv-java OS 환경변수 우선순위 | ✅ 완료 | `System.getenv(key) == null` 체크 후 setProperty |
| `.env.example` 보안 가이드 | ✅ 완료 | 인라인 주석 제거 (dotenv-java 호환) |
| Spring Boot Gradle plugin | ✅ 완료 | 3.3.18 (CVE-2026-22733 대응) |
| PR 생성 | ✅ 완료 | [pocat-batch PR](https://github.com/POCAT-sparta/pocat-batch/pulls) |

---

## 병행 운영 체크리스트 (ADR-003 종료 기준)

> 아래 항목 **모두** 충족 후 메인 앱 스케줄러 비활성화 가능.

- [ ] 7일 이상 연속 배치 Job 오류율 0% 유지
- [ ] `view:free:buffer:processing:failed`, `comment:free:buffer:processing:failed` 키 미누적
- [ ] 데이터 정합성 검증 완료 (메인 앱 vs 배치 서버 처리 결과 일치)
- [ ] 에러율 < 0.1%, 평균 Job 처리 시간 < 5초

---

## 메인 앱 스케줄러 폐기 현황

| 스케줄러 | 상태 |
|----------|------|
| `FreePostRankingScheduler` | 병행 운영 중 (유지) |
| `ViewCountFlushScheduler` | 병행 운영 중 (유지) |

> 위 종료 기준 충족 후 비활성화 또는 삭제 예정.
