# ADR-015: Redis 단일 인스턴스에서 Cluster 모드로 전환

| 항목 | 내용 |
|------|------|
| **날짜** | 2026-06-09 |
| **상태** | Accepted |
| **결정자** | POCAT 팀 |

---

## 맥락 (Context)

현재 POCAT은 단일 Redis 인스턴스를 사용하며, 다음 세 가지 용도를 담당하고 있다.

| 용도 | 상세 |
|------|------|
| 경매 분산 락 (Redisson RLock) | 경매 활성화·만료·낙찰 Tasklet 단일 실행 보장 |
| 캐시 | 닉네임, 인기 랭킹, 세션 등 일반 캐시 |
| Pub/Sub | 알림·WebSocket 메시지 전파 |

### 문제: 단일 인스턴스 SPOF

경매 만료 시점(19:00~19:30) Redis 인스턴스에 장애가 발생하면 `AuctionExpirationBackupTasklet`이 Redisson RLock 획득에 실패한다. 이 경우 경매 결과 누락 또는 중복 낙찰이 발생할 수 있으며, ADR-014에서 배치 서버로 이관된 스케줄러들이 Redis 없이 분산 락을 보장할 수 없다.

### 필요성

- **HA 확보**: master 장애 시 replica 자동 승격으로 경매 크리티컬 구간 가용성 유지
- **수평 확장 준비**: 슬롯 분산 구조로 향후 노드 추가 가능
- **운영 경험 확보**: 프로덕션 투입 전 클러스터 모드 운영 패턴 습득

---

## 결정 (Decision)

### 1. 클러스터 구성 — 단일 EC2 docker-compose (6노드)

| 역할 | 노드 수 | 비고 |
|------|---------|------|
| master | 3 | 슬롯 0–5460, 5461–10922, 10923–16383 |
| replica | 3 | 각 master 1:1 |

단일 EC2에서 docker-compose로 6노드를 기동한다. 목적은 **가용성(multi-AZ)이 아닌 클러스터 모드 기능 활성화와 운영 경험 습득**이다. 노드 간 네트워크 지연은 localhost 내 통신이므로 무시 가능.

### 2. EC2 인스턴스 — t3.medium

| 인스턴스 | 메모리 | 판단 |
|----------|--------|------|
| t3.small | 2 GB | OS + Redis 6노드 합산 시 부족. RDB fork(Copy-on-Write) 메모리 spike 흡수 불가 → **기각** |
| **t3.medium** | **4 GB** | 6노드 × maxmemory 400 MB = 2.4 GB. 1.6 GB 여유. CPU 버스트 크레딧으로 경매 만료 spike 흡수 가능 → **선택** |
| t3.large 이상 | 8 GB+ | Redis 용도가 일반 캐시 전용(Vector DB 미사용)이므로 과스펙 → **기각** |

`maxmemory 400mb`는 단일 노드 기준이며, 클러스터 전체 데이터 용량은 슬롯 분산으로 노드당 실제 사용량이 더 낮다.

### 3. Eviction 정책 — allkeys-lru

캐시 전용 Redis 용도에 최적. TTL 없는 키도 메모리 압박 시 LRU로 자동 제거하여 OOM을 방지한다.

### 4. 클라이언트 — Lettuce (기존 유지)

Lettuce는 클러스터 Pub/Sub 메시지를 모든 master 노드에 자동 전파(broadcast)한다. Jedis는 클러스터 환경에서 단일 노드 Pub/Sub만 지원하여 알림 유실 위험이 있다. 기존 Spring Boot 기본 클라이언트인 Lettuce를 그대로 유지하며 `ClusterTopologyRefreshOptions`를 활성화한다.

### 5. 분산 락 — Redisson useClusterServers()

`useClusterServers()`로 전환하면 Redisson이 슬롯 기반 라우팅을 자동 처리한다. 단일 인스턴스용 `useSingleServer()` 설정만 변경하면 되며, `RLock` API는 변경 없음.

### 6. CROSSSLOT 해결 — Pipeline 개별 처리

Redis Cluster는 여러 key가 서로 다른 슬롯에 속할 경우 단일 명령으로 처리하는 multi-key 연산을 허용하지 않는다. 프로젝트 내 두 곳에서 이 패턴을 사용 중이므로 아래와 같이 수정한다.

| 클래스 | 기존 | 변경 |
|--------|------|------|
| `UserNicknameCacheService` | `mget`/`mset` (multi-key) | `executePipelined` 내 개별 `get`/`set` |
| `PostCommentCacheEvictor` | `delete(Collection<K>)` (multi-key) | `executePipelined` 내 개별 `delete` |

Hash Tag(`{tag}`) 방식은 슬롯을 강제로 같은 곳에 몰아 클러스터 이점을 제거하므로 적용하지 않는다.

---

## 거부된 대안 (Rejected Options)

| 대안 | 기각 이유 |
|------|----------|
| **Redis Sentinel** | HA(master 자동 승격)만 제공, 샤딩 없음. 수평 확장 준비 요건 미충족 |
| **AWS ElastiCache Cluster Mode** | 운영 복잡도를 AWS에 위임 가능하나 로컬 개발 환경에서 완전 재현 불가. docker-compose 기반 로컬 개발 일관성 파괴 |
| **단일 인스턴스 유지** | SPOF 미해결. 경매 크리티컬 구간 Redis 장애 시 데이터 정합성 보장 불가 |

---

## 영향 (Consequences)

### 긍정적 효과

- master 장애 시 replica 자동 승격 (~5초 이내) — 경매 만료 구간 가용성 확보
- 슬롯 분산 구조로 향후 노드 추가 시 무중단 리샤딩 가능
- Redisson, Lettuce 모두 클러스터 자동 라우팅 지원으로 애플리케이션 코드 변경 최소화

### 부정적 효과 / 주의사항

- CROSSSLOT 오류 발생 가능 지점 사전 점검 필수 (`UserNicknameCacheService`, `PostCommentCacheEvictor` 수정 필요)
- 단일 EC2 구성이므로 EC2 인스턴스 자체 장애 시 전체 클러스터 다운 (multi-AZ 대비 낮은 HA)
- docker-compose 클러스터 노드 수 증가로 로컬 개발 환경 메모리 요구량 증가
- Lettuce `ClusterTopologyRefreshOptions` 미설정 시 노드 변경(failover) 감지 지연 가능

---

## 구현 체크리스트

- [ ] EC2 t3.medium 프로비저닝 및 docker-compose 6노드 클러스터 구성
- [ ] `redis.conf`: `maxmemory 400mb`, `maxmemory-policy allkeys-lru`, `cluster-enabled yes`
- [ ] `application.yml`: `spring.data.redis.cluster.nodes` 설정 (6노드 주소)
- [ ] Lettuce `ClusterTopologyRefreshOptions` 활성화 (`enablePeriodicRefresh`, `enableAdaptiveRefreshTrigger`)
- [ ] Redisson 설정: `useSingleServer()` → `useClusterServers()`
- [ ] `UserNicknameCacheService`: `mget`/`mset` → `executePipelined` 개별 처리로 변경
- [ ] `PostCommentCacheEvictor`: `delete(Collection)` → `executePipelined` 개별 `delete`로 변경
- [ ] 통합 테스트: Testcontainers Redis Cluster 또는 `embedded-redis-cluster` 전환
- [ ] 로컬 개발 docker-compose: 단일 Redis → 6노드 클러스터로 교체

---

## 모니터링

| 항목 | 감시 대상 | 방법 |
|------|-----------|------|
| Failover 감지 | master 장애 → replica 승격 소요 시간 | Redis `CLUSTER INFO`, APM alert |
| 메모리 사용량 | 노드별 `used_memory` / `maxmemory` 비율 | Prometheus redis_exporter |
| CROSSSLOT 오류 | `CROSSSLOT Keys in request don't hash to the same slot` 에러 로그 | 로그 집계 |
| Redisson 락 실패 | `RLock.tryLock()` 타임아웃 비율 | APM 메트릭 |
| Pub/Sub 전파 지연 | 알림 발행 → 수신 시간 | WebSocket e2e 지연 측정 |

---

## 보안 고려사항

| 항목 | 로컬 개발 | 프로덕션 |
|------|---------|---------|
| `protected-mode` | `no` (Docker 네트워크 내부 전용) | AWS VPC 보안그룹으로 제어 |
| `bind` | `0.0.0.0` (컨테이너 내부) | VPC 내부 IP만 허용 |
| 포트 노출 | `127.0.0.1:700X` (호스트 로컬만) | 보안그룹 인바운드 차단 |
| 패스워드 | 선택적 (`REDIS_PASSWORD` env var) | 필수 (`REDIS_PASSWORD` Parameter Store) |
| TLS | 미사용 (로컬 개발) | 향후 `rediss://` 전환 검토 |

---

## 구현 후 검증 체크리스트

- [ ] `redis-cli -p 7001 cluster info` → `cluster_state:ok`, `cluster_slots_assigned:16384`
- [ ] Spring Boot Actuator `/actuator/health` → `redis: UP`
- [ ] 경매 만료 이벤트 수신 확인 (`AuctionExpirationRedisSubscriber`)
- [ ] 주문 만료 이벤트 수신 확인 (`ExpiryEventListener`)
- [ ] Redisson RLock 입찰/즉시구매 정상 동작 확인
- [ ] `./gradlew test` GREEN

---

## 관련 문서

- [ADR-002: 경매 인기 랭킹 — Redis ZSet](ADR-002-auction-popular-ranking.md)
- [ADR-004: 경매 생명주기 — Redis + Kafka](ADR-004-auction-lifecycle-redis-kafka.md)
- [ADR-014: 스케줄러 배치 서버 이전](ADR-014-scheduler-batch-migration-#171.md) — Redisson RLock 기반 경매 Tasklet
- [ADR-003: 캐싱 전략](ADR-003-caching-strategy.md)
