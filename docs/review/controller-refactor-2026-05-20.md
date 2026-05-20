# 컨트롤러 매핑 리팩토링 보고서 (2026-05-20)

## 요약
모든 도메인 컨트롤러에서 API 버전 관리(`v1`)를 클래스 레벨의 `@RequestMapping`에서 개별 메서드 레벨의 HTTP 매핑 어노테이션으로 이동하는 리팩토링을 수행하였습니다.

## 주요 변경 사항
- 총 18개의 컨트롤러 업데이트 완료.
- 클래스 레벨의 `@RequestMapping` 경로를 `/api/v1/...`에서 `/api`로 일괄 변경.
- 모든 메서드(`@GetMapping`, `@PostMapping`, `@PatchMapping`, `@PutMapping`, `@DeleteMapping`)에 버전이 포함된 전체 경로 명시 (예: `/v1/auth/login`).

## 대상 파일 목록
1. `AuctionController.java`
2. `AuthController.java`
3. `AuctionBidController.java`
4. `AdminCardController.java`
5. `CardController.java`
6. `ChatController.java`
7. `CommentController.java`
8. `FreePostController.java`
9. `TradePostController.java`
10. `LikeController.java`
11. `NotificationController.java`
12. `AdminOrderController.java`
13. `OrderController.java`
14. `PaymentController.java`
15. `AdminRefundController.java`
16. `RefundController.java`
17. `AdminSettlementController.java`
18. `SettlementController.java`

## 리팩토링 사유
클래스 단위로 고정되었던 API 버전을 엔드포인트 단위로 세분화하여 관리할 수 있도록 구조를 개선하였습니다. 이를 통해 향후 특정 API만 버전을 올리는 등 유연한 운영이 가능해집니다.
