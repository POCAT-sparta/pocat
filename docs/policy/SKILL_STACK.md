이미지에서 색상별로 분류해서 정리해 드립니다.

---

## 🔵 파란색 — 기본 구현

| 카테고리 | 항목 |
|---|---|
| 인프라 | CI/CD (GitHub Actions), ECR·ECS, Auto Scaling, 무중단 배포 (Rolling), CI/CD (Jenkins, 오픈소스/완전무료) |
| DB | MySQL |
| 검색 | Index, 쿼리튜닝 |
| 성능 | Redis, 캐싱 |
| 아키텍처 | CQRS |
| API 문서화 | Notion, Swagger |
| 모니터링 | Log Trace, Grafana, Prometheus |
| 테스트 | JUnit, 통합테스트, 동시성 테스트, 부하테스트 |
| 보안 | JWT, Spring Security, CORS |
| 코드 품질 | PR 리뷰, AI 리뷰 (code rabbit), Sonar Qube |
| 동시성 | 분산락, Rate Limit |

---

## 🟣 보라색 — 웬만하면 구현할 고도화 기능

| 카테고리 | 항목 |
|---|---|
| 검색 | ElasticSearch |
| 코드 품질 | AI 리뷰 (code rabbit) |
| 보안 | CORS |
| 동시성 | 비관락, 낙관락 |

---

## 🔴 빨간색 — 시간이 남으면 구현

| 카테고리 | 항목 |
|---|---|
| 아키텍처 | MSA |
| API 문서화 | RestDocs |
| 보안 | OAuth |

---

> **요약**
> - 파란색(기본): CI/CD, MySQL, Redis, JUnit, JWT, Grafana 등 핵심 인프라·DB·테스트·보안 스택 위주
> - 보라색(고도화): ElasticSearch, 비관락/낙관락, CORS 등 성능·안정성 강화 항목
> - 빨간색(선택): MSA, RestDocs, OAuth — 시간 여유가 생길 때 도전하는 구조 고도화 영역