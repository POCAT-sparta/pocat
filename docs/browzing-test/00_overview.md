# POCAT 브라우저 테스트 시나리오 개요

## 프로젝트 개요

POCAT은 포켓몬 카드 거래 및 경매 플랫폼입니다.

## 테스트 환경

### 필수 조건

- 서버 기동 확인 (Spring Boot)
- 외부 서비스 연결 확인
  - MySQL
  - Redis
  - Elasticsearch
  - Kafka
  - AWS S3
  - PortOne (결제)

### 기본 URL

```text
BASE_URL: http://localhost:8080
```

### 테스트 계정 준비

| 역할 | 이메일 | 비밀번호 | 용도 |
|------|--------|----------|------|
| 일반 사용자 A | user_a@test.com | Test1234! | 판매자 역할 |
| 일반 사용자 B | user_b@test.com | Test1234! | 구매자 역할 |
| 어드민 | admin@pocat.com | (어드민 비밀번호) ⚠️ 실행 전 설정 필요 | 관리자 기능 |

### 인증 헤더

```text
Authorization: Bearer {accessToken}
```

---

## 테스트 시나리오 목록

| 파일 | 테스트 범위 | 우선순위 |
|------|------------|----------|
| [01_auth.md](01_auth.md) | 회원가입 / 로그인 / 토큰 갱신 / 로그아웃 | 최상 |
| [02_card.md](02_card.md) | 카드 목록 / 상세 / 등록 신청 / 승인 | 상 |
| [03_auction.md](03_auction.md) | 경매 생성 / 입찰 / 즉시구매 / 취소 | 최상 |
| [04_order_payment.md](04_order_payment.md) | 주문 생성 / 결제 / 결제 확인 | 최상 |
| [05_refund_settlement.md](05_refund_settlement.md) | 환불 요청 / 승인 / 정산 처리 | 상 |
| [06_community.md](06_community.md) | 자유게시판 / 거래게시판 / 댓글 / 좋아요 | 중 |
| [07_chat.md](07_chat.md) | 채팅방 생성 / 메시지 전송 (WebSocket) | 상 |
| [08_notification.md](08_notification.md) | 알림 목록 / 읽음 처리 | 중 |
| [09_admin.md](09_admin.md) | 카드 승인 / 경매 검수 / 환불 처리 / 정산 완료 | 최상 |
| [10_ai.md](10_ai.md) | 카드 분석 / AI 어시스턴트 채팅 | 중 |
| [11_user_profile.md](11_user_profile.md) | 프로필 / 빌링키 / 찜 목록 | 중 |
| [12_e2e_scenarios.md](12_e2e_scenarios.md) | E2E 통합 시나리오 | 상 |

---

## 테스트 실행 순서 (권장)

```text
1. 인증 (01_auth) → 토큰 획득
2. 어드민 - 카드 승인 (09_admin, 02_card) → 테스트용 카드 준비
3. 경매 생성 (03_auction) → 경매 등록
4. 입찰 및 즉시구매 (03_auction) → 주문 생성
5. 결제 (04_order_payment) → 결제 완료
6. 환불 / 정산 (05_refund_settlement)
7. 커뮤니티 / 채팅 / 알림 (06~08)
8. AI 기능 (10_ai)
```

---

## 공통 에러 코드

| HTTP 상태 | 의미 | 확인 항목 |
|-----------|------|-----------|
| 400 | 잘못된 요청 (유효성 검사 실패) | 요청 바디/파라미터 확인 |
| 401 | 인증 실패 (토큰 없음/만료) | Authorization 헤더 확인 |
| 403 | 권한 부족 | 사용자 역할(ROLE) 확인 |
| 404 | 리소스 없음 | ID 값 확인 |
| 409 | 중복/충돌 | 이미 처리된 요청 확인 |
| 429 | 요청 한도 초과 (Rate Limit) | 대기 후 재시도 |
| 500 | 서버 오류 | 서버 로그 확인 |
