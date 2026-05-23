# 테스트 리포트 — #97 종합 단위 테스트 스위트

## 1. 요약

| 항목 | 내용 |
|------|------|
| 브랜치 | `test/comprehensive-suite/#97` |
| 작성일 | 2026-05-24 |
| 총 테스트 수 | 269 |
| 실패 | 0 |
| 비활성화 | 2 (`PocatApplicationTests` — Redis 인프라 필요; `AdminUserCommandServiceTest` — scaffold) |

---

## 2. 커버리지 범위

| 도메인 | 서비스 레이어 | 컨트롤러 레이어 |
|--------|--------------|----------------|
| Payment | PaymentCommandService, PaymentQueryService, PaymentFailureService | PaymentController |
| Settlement | SettlementCommandService, SettlementQueryService, AdminSettlementCommandService, AdminSettlementQueryService | SettlementController, AdminSettlementController |
| Order | OrderCommandService, OrderQueryService, AdminOrderQueryService | OrderController, AdminOrderController |
| Refund | RefundCommandService, RefundQueryService | RefundController, AdminRefundController |
| Auction | AuctionCommandService, AuctionQueryService | AuctionController |
| Bid | AuctionBidCommandService | — |
| Auth | AuthService | AuthController |
| User | UserCommandService, UserQueryService, AdminUserCommandService *(scaffold)* | UserController |
| FreePost | FreePostCommandService, FreePostQueryService | FreePostController |

---

## 3. 테스트 인프라

- **실행 방식**: `@ExtendWith(MockitoExtension.class)` — Spring 컨텍스트·DB 미사용, 순수 단위 테스트
- **TestFixtures.java**: User, Order, Payment, Card 등 공유 도메인 픽스처 모음
- **TestCustomUserDetails.java**: `protected` 생성자를 우회하기 위한 Security 테스트 전용 서브클래스

---

## 4. Phase 4 리뷰 반영 사항

| 지적 사항 | 해결 방법 |
|-----------|----------|
| 락 해제 경로 미검증 (`AuctionBidCommandService`) | `TransactionSynchronization` 콜백은 Mockito-only 환경에서 실행되지 않는 설계 의도임을 `AuctionBidCommandServiceTest` 주석에 명시 |
| 결제 초과 금액 불일치 케이스 누락 | `PaymentCommandServiceTest`에 over-amount mismatch 테스트 추가 |
| 웹훅 서명 헤더 누락 케이스 미검증 | missing-signature-header 테스트 추가 |
| `GlobalExceptionHandler` — `MissingRequestHeaderException` 응답 코드 오류 | 500 → 400으로 수정 |
| 웹훅 테스트 display name 오해 소지 | display name 문구 수정 |

### 외부 코드 리뷰 반영 (Phase 4 추가)

| 지적 사항 | 해결 방법 |
|-----------|----------|
| `AuthControllerTest` logout Bearer 파싱 검증 누락 | `verify(authService).logout(eq("validAccessToken"))` 추가 |
| `AdminOrderControllerTest` 검색 조건 전달 값 미검증 | `ArgumentCaptor<AdminOrderSearchCondition>`으로 `orderStatus` 값 검증 추가 |
| `PaymentControllerTest` 서비스 미호출 검증 누락 | 시그니처 헤더 없을 때 `verifyNoInteractions(paymentCommandService)` 추가 |
| `PaymentCommandServiceTest` 멱등성 테스트 불완전 | `verify(settlementCommandService, never()).createSettlement(anyString())` 추가 |
| `PaymentFailureServiceTest` DisplayName 오류 | 에러 코드 설명 `PAYMENT_NOT_FOUND` → `ORDER_NOT_FOUND` 수정 |
| `AdminRefundControllerTest` `argThat` 정적 import 누락 | `import static org.mockito.ArgumentMatchers.argThat;` 추가 |
| `AdminRefundControllerTest` 거절 사유 값 미검증 | `argThat` 람다로 `rejectReason()` 실제 값 검증 |
| `AdminUserCommandServiceTest` SUT 미구현 scaffold 명시 | `@Disabled("AdminUserCommandService 미구현 — 서비스 클래스 생성 후 활성화")` 추가 |

---

## 5. 알려진 한계 / 향후 작업

| 항목 | 사유 | 대응 방향 |
|------|------|-----------|
| 웹훅 HMAC 서명 검증 테스트 불가 | `handleWebhook` 서비스 미구현 (`PORTONE_NOT_INTEGRATED` 예외 발생) | PortOne 연동 완료 후 테스트 추가 |
| 컨트롤러 Admin 역할 강제 미검증 | `standaloneSetup`이 Spring Security 필터 체인을 우회 | `@SpringBootTest` + `MockMvc` 슬라이스 테스트로 별도 보안 검증 필요 |
| AdminUserCommandService 테스트 비활성화 | `AdminUserCommandService` 미구현 — SUT 호출 없는 위양성 테스트를 `@Disabled` 처리 | 서비스 구현 후 `AdminUserCommandServiceTest` 활성화 |
