# Redis Cluster CROSSSLOT 수정 테스트 결과

| 항목 | 내용 |
|------|------|
| 이슈 | #194 (backend), #5 (batch) |
| 날짜 | 2026-06-09 |
| 브랜치 | feat/redis-cluster/#194 / fix/auction-event-payload/#5 |

## 수정 내역

### 원인
Redis Cluster에서 단일 명령으로 서로 다른 슬롯의 키를 처리하면 `CROSSSLOT Keys in request don't hash to the same slot` 오류 발생.

### 수정 파일 및 방법

| 파일 | 기존 키 | 변경 후 키 | 이유 |
|------|---------|-----------|------|
| `AuthService.java` | `login:fail:{email}` (미적용) | `login:fail:{email@domain}` | Lua KEYS[1,2] 동일 슬롯 |
| `FreePostRankingService.java` | `ranking:free:popular` | `{ranking:free}:popular` | RENAME 양 키 동일 슬롯 |
| `AuctionRankingService.java` (backend) | `ranking:auction:popular` | `{ranking:auction}:popular` | RENAME 양 키 동일 슬롯 |
| `FreePostRankingTasklet.java` | `ranking:free:popular` | `{ranking:free}:popular` | RENAME 동일 슬롯 |
| `ViewCountFlushTasklet.java` | `view:free:buffer` 外 3개 | `{view:free}:buffer` 外 | RENAME 동일 슬롯 |
| `AuctionRankingService.java` (batch) | `ranking:auction:popular` | `{ranking:auction}:popular` | stagingKey RENAME 동일 슬롯 |
| `RedissonConfig.java` (batch) | `useSingleServer()` | `useClusterServers()` | Cluster 라우팅 지원 |

### Hash Tag 원리
`{tag}:suffix` 형식에서 Redis CRC16은 `tag` 부분만 계산. 동일 tag를 공유하는 키들은 항상 동일 슬롯에 배치됨.

## 코드 리뷰 결과

- **REVIEW**: APPROVED — 8개 파일 전체 hash tag 일관성 확인
- **SECURITY**: PASS

### 보안 검토 주요 항목

| 항목 | 결과 |
|------|------|
| AuthService email 특수문자 | 안전 — fail/lock 양 키 동일 email 값 사용으로 항상 동일 슬롯 |
| batch stagingKey UUID | 안전 — `{ranking:auction}:popular:staging:UUID`에서 hash tag는 `{ranking:auction}` (UUID suffix 무관) |
| leaseTime 미명시 | 기존 코드 (AuctionCommandService, AuctionBuyoutService) — 이번 PR 범위 외, 별도 이슈 추적 필요 |

## 후속 조치
- [ ] Redisson `tryLock` leaseTime 명시적 설정 (#194 이후 별도 이슈)
- [ ] Testcontainers Redis Cluster 전환으로 통합 테스트 보강
