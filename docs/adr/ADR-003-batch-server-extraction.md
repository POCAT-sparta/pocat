# ADR-003: 배치 서버 분리 (pocat-batch)

| 항목 | 내용 |
|------|------|
| **Status** | Proposed |
| **Date** | 2026-05-24 |
| **Deciders** | POCAT 팀 |

---

## Context

POCAT 메인 앱은 현재 두 개의 `@Scheduled` 스케줄러를 내장하고 있다.

- `FreePostRankingScheduler` — 자유게시판 인기 랭킹 Redis ZSet 갱신
- `ViewCountFlushScheduler` — 자유게시판 조회수·댓글수 Redis 버퍼 → MySQL 플러시

단일 서버 환경에서는 문제없으나, 다중 앱 서버 스케일아웃 시 **각 인스턴스가 독립적으로 스케줄러를 실행**하여 동일 Job이 중복 실행되는 문제가 발생한다. 특히 `ViewCountFlushScheduler`는 Redis 버퍼를 RENAME 후 MySQL에 반영하는 방식이므로, 중복 실행 시 데이터 정합성 문제로 이어질 수 있다.

---

## Decision

별도 `pocat-batch` 레포를 구축하고 자유게시판 스케줄링 Job 2개를 Spring Batch Tasklet 기반으로 분리 구현한다. 이 구현은 **팀원 참고용 샘플**로서, 향후 다른 도메인 Job 추가 시 동일 패턴을 따른다.

메인 앱의 기존 스케줄러는 **당분간 병행 운영**한다. 아래 조건을 모두 충족한 후 단계적으로 폐기한다.

**병행 운영 종료 기준 (checklist):**
- [ ] 7일 이상 연속으로 배치 Job 오류율 0% 유지
- [ ] `view:free:buffer:processing:failed`, `comment:free:buffer:processing:failed` 키 미누적 (backlog 없음)
- [ ] 데이터 정합성 검증 완료 (메인 앱과 배치 서버 처리 결과 일치)
- [ ] 에러율 < 0.1%, 평균 Job 처리 시간 < 5초 기준 충족

---

## Migration Plan (단계적 폐기)

| 단계 | 내용 | 담당 |
|------|------|------|
| 1 | pocat-batch 서버 로컬 및 스테이징 환경 검증 | 개발팀 |
| 2 | ShedLock 도입 검토 (다중 배치 인스턴스 대비) | 아키텍처 검토 후 별도 ADR |
| 2.5 | 운영 배포 후 **병행 운영 기간** 진행 | 개발팀 + 운영팀 |
| | — FreePostRankingScheduler, ViewCountFlushScheduler 유지 상태에서 배치 서버 병행 가동 | |
| | — 아래 **모니터링 항목** 수집 및 종료 기준 달성 확인 | |
| | — 롤백 조건: 배치 서버 오류율 > 1% 또는 데이터 불일치 감지 시 배치 서버 중단 | |
| 3 | 위 종료 기준 충족 후 메인 앱 `FreePostRankingScheduler`, `ViewCountFlushScheduler` 비활성화 또는 삭제 | 개발팀 |

---

## Consequences

**긍정적 효과**
- 메인 앱 스케일아웃 시 스케줄러 중복 실행 문제 해소
- 스케줄링 관련 로직을 메인 앱에서 분리하여 단일 책임 원칙 준수
- Spring Batch 메타테이블을 통한 Job 실행 이력 영속 관리

**부정적 효과 / 주의사항**
- 병행 운영 기간 중 동일 작업이 메인 앱 + 배치 서버에서 각 1회씩 총 2회 수행됨
- 운영 전환 시 메인 앱 스케줄러를 반드시 비활성화해야 함 (미비활성화 시 중복 실행 지속)
- 배치 서버 추가로 인프라 관리 포인트 증가

**모니터링 항목 (병행 운영 기간)**

| 항목 | 감시 대상 | 방법 |
|------|-----------|------|
| 중복 플러시 감지 | 메인 앱 vs 배치 서버 flushFreeView/flushFreeComment 실행 건수 비교 | 로그 집계 (`flushTrade/flushFreeView/flushFreeComment failed` 서버별 카운트) |
| processingKey 잔류 | `view:free:buffer:processing`, `comment:free:buffer:processing` 키가 다음 사이클에도 잔류하는지 | `진행이 안된 데이터 발견…` 경고 로그 반복 여부 및 처리 지연 시간 |
| failedKey 누적 | `view:free:buffer:processing:failed`, `comment:free:buffer:processing:failed` 키 크기 및 만료 전 backlog 지속 여부 | `flush failed for entry…` 로그 + Redis key 크기 모니터링 |
| 랭킹 정합성 | 메인 앱과 배치 서버 `ranking:free:popular` ZSet 내용 일치 여부 | 주기적 ZRANGE 비교 |

---

## Related

- [pocat-batch 레포](https://github.com/POCAT-sparta/pocat-batch) `docs/ADR-001-spring-batch-separation.md` — 배치 서버 내부 결정 배경
- [pocat-batch 레포](https://github.com/POCAT-sparta/pocat-batch) `docs/ARCHITECTURE.md` — 패키지 구조 및 Job 흐름

구현 현황은 [docs/adr/IMPLEMENTATION-003.md](IMPLEMENTATION-003.md) 참조.
