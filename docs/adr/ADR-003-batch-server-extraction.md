# ADR-003: 배치 서버 분리 (pocat-batch)

| 항목 | 내용 |
|------|------|
| **Status** | Accepted |
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

메인 앱의 기존 스케줄러는 **당분간 병행 운영**하며, pocat-batch 서버의 안정성 확인 후 단계적으로 폐기한다.

---

## Migration Plan (단계적 폐기)

| 단계 | 내용 | 담당 |
|------|------|------|
| 1 | pocat-batch 서버 로컬 및 스테이징 환경 검증 | 개발팀 |
| 2 | ShedLock 도입 검토 (다중 배치 인스턴스 대비) | 아키텍처 검토 후 별도 ADR |
| 3 | 메인 앱 `FreePostRankingScheduler`, `ViewCountFlushScheduler` `@Scheduled` 비활성화 또는 클래스 삭제 | 개발팀 |

---

## Consequences

**긍정적 효과**
- 메인 앱 스케일아웃 시 스케줄러 중복 실행 문제 해소
- 스케줄링 관련 로직을 메인 앱에서 분리하여 단일 책임 원칙 준수
- Spring Batch 메타테이블을 통한 Job 실행 이력 영속 관리

**부정적 효과 / 주의사항**
- 병행 운영 기간 중 동일 작업이 메인 앱 + 배치 서버에서 각 1회씩 총 2회 수행됨
  - 조회수·댓글수 중복 플러시 여부 모니터링 필요
- 운영 전환 시 메인 앱 스케줄러를 반드시 비활성화해야 함 (미비활성화 시 중복 실행 지속)
- 배치 서버 추가로 인프라 관리 포인트 증가

---

## Related

- `pocat-batch` 레포 `docs/ADR-001-spring-batch-separation.md` — 배치 서버 내부 결정 배경
- `pocat-batch` 레포 `docs/ARCHITECTURE.md` — 패키지 구조 및 Job 흐름

---

## 구현 완료 현황 (2026-05-24)

- **pocat-batch 레포**: Spring Batch 인프라 구현 완료
  - Job 2개: `freePostRankingJob`, `viewCountFlushJob`
  - 브랜치: `feat/spring-batch-infra`
- **Phase 4 리뷰/보안 수정 완료**: Redis rename 가드, null 체크, dotenv 우선순위, Redis password 지원
- **다음 단계**: PR 머지 → 운영 배포 → 메인 앱 스케줄러 비활성화
