# POCAT 브라우저 테스트 전체 시나리오

> **작성일**: 2026-06-11  
> **프로젝트**: POCAT (포켓몬 카드 거래/경매 플랫폼)  
> **BASE URL**: `http://localhost:8080`

---

## 테스트 계정

| 역할 | 이메일 | 비밀번호 |
|------|--------|----------|
| 판매자 (User A) | user_a@test.com | Test1234! |
| 구매자 (User B) | user_b@test.com | Test1234! |
| 어드민 | admin@pocat.com | (어드민 비밀번호) |

---

## 전체 테스트 케이스

| TC ID | 분류 | 우선순위 | 설명 | Method | API Path | 인증 | 주요 확인 항목 | 결과 |
|-------|------|----------|------|--------|----------|------|----------------|------|
| TC-AUTH-001 | 인증 | 최상 | 회원가입 성공 | POST | /api/v1/auth/signup | 불필요 | 201 응답 / userId·email·nickname 포함 / 동일 이메일 재가입 시 409 | ⬜ |
| TC-AUTH-002 | 인증 | 상 | 회원가입 유효성 검사 실패 | POST | /api/v1/auth/signup | 불필요 | 이메일 형식 오류→400 / 비밀번호 미충족→400 / 닉네임 빈값→400 / 전화번호 형식 오류→400 | ⬜ |
| TC-AUTH-003 | 인증 | 최상 | 로그인 성공 + 토큰 발급 | POST | /api/v1/auth/login | 불필요 | 200 응답 / accessToken·refreshToken 발급 / JWT 형식(. 구분 3파트) 확인 | ⬜ |
| TC-AUTH-004 | 인증 | 상 | 로그인 실패 - 잘못된 비밀번호 | POST | /api/v1/auth/login | 불필요 | 401 또는 400 응답 / 에러 메시지 존재 | ⬜ |
| TC-AUTH-005 | 인증 | 상 | 토큰 갱신 (Reissue) | POST | /api/v1/auth/reissue | refreshToken | 200 응답 / 새 accessToken 발급 / 만료 토큰→401 | ⬜ |
| TC-AUTH-006 | 인증 | 상 | 로그아웃 + 토큰 블랙리스트 | POST | /api/v1/auth/logout | 필요 | 200 응답 / 로그아웃 후 기존 토큰으로 API 호출 시 401 | ⬜ |
| TC-AUTH-007 | 인증 | 중 | Rate Limit 검증 | POST | /api/v1/auth/login | 불필요 | 10회 초과 시 429 응답 / 회원가입 5회 초과 시 429 | ⬜ |
| TC-USER-001 | 사용자 | 상 | 내 프로필 조회 | GET | /api/v1/users/me | 필요 | 200 응답 / userId·email·nickname·phone·address 포함 / password 미노출 / 인증 없이 401 | ⬜ |
| TC-USER-002 | 사용자 | 중 | 내 프로필 수정 | PATCH | /api/v1/users/me | 필요 | 200 응답 / 수정 필드 반영 / 중복 닉네임→409 | ⬜ |
| TC-USER-003 | 사용자 | 상 | 빌링키 등록 | POST | /api/v1/users/me/billing-key | 필요 | 200 응답 / 계정에 빌링키 연결 확인 | ⬜ |
| TC-USER-004 | 사용자 | 중 | 빌링키 수정 | PUT | /api/v1/users/me/billing-key | 필요 | 200 응답 / 기존 빌링키 교체 확인 | ⬜ |
| TC-USER-005 | 사용자 | 중 | 빌링키 삭제 | DELETE | /api/v1/users/me/billing-key | 필요 | 200 응답 / 삭제 후 자동결제 불가 확인 | ⬜ |
| TC-CARD-001 | 카드 | 상 | 카드 목록 조회 (필터) | GET | /api/v1/cards | 불필요 | 200 응답 / 페이지네이션(content·totalElements·totalPages) / keyword·seriesId·grade 필터 동작 | ⬜ |
| TC-CARD-002 | 카드 | 상 | 카드 상세 조회 | GET | /api/v1/cards/{cardId} | 불필요 | 200 응답 / name·series·set·grade·rarity·status 포함 / 존재하지 않는 ID→404 | ⬜ |
| TC-CARD-003 | 카드 | 상 | 카드 경매 목록 조회 | GET | /api/v1/cards/{cardId}/auctions | 불필요 | 200 응답 / 해당 카드의 진행 중인 경매 목록 / auctionId·title·currentPrice·endedAt 포함 | ⬜ |
| TC-CARD-004 | 카드 | 중 | 카드 평균 가격 조회 | GET | /api/v1/cards/{cardId}/average-price | 불필요 | 200 응답 / averagePrice 필드 존재 / 거래 내역 없는 카드 0 또는 null 처리 | ⬜ |
| TC-CARD-005 | 카드 | 상 | 카드 등록 신청 (이미지 업로드) | POST | /api/v1/cards/upload | 필요 | 201 응답 / status: PENDING / S3 imageUrl 반환 / 인증 없이 401 | ⬜ |
| TC-CARD-006 | 카드 | 중 | 내 카드 신청 목록 조회 | GET | /api/v1/cards/my-requests | 필요 | 200 응답 / 본인 신청 카드만 반환 / PENDING·APPROVED·REJECTED status 표시 | ⬜ |
| TC-CARD-007 | 카드 | 중 | 시리즈 목록 조회 | GET | /api/v1/series | 불필요 | 200 응답 / id·name·nameKo 포함 | ⬜ |
| TC-CARD-008 | 카드 | 중 | 세트 목록 조회 (seriesId 필터) | GET | /api/v1/sets | 불필요 | 200 응답 / seriesId 필터 동작 | ⬜ |
| TC-AUCTION-001 | 경매 | 최상 | 경매 생성 | POST | /api/v1/auctions | 필요 | 201 응답 / status: PENDING / auctionId 반환 / 이미 경매 중인 카드 재등록→409 | ⬜ |
| TC-AUCTION-002 | 경매 | 상 | 인기 경매 목록 조회 | GET | /api/v1/auctions/popular | 불필요 | 200 응답 / 인기순 정렬 확인 / 캐싱 확인(연속 호출 시 동일 결과) | ⬜ |
| TC-AUCTION-003 | 경매 | 상 | 경매 목록 검색 및 필터 | GET | /api/v1/auctions | 불필요 | 200 응답 / keyword·status·minPrice·maxPrice·grade 필터 / HIGHEST_PRICE·LATEST·ENDING_SOON 정렬 | ⬜ |
| TC-AUCTION-004 | 경매 | 상 | 경매 상세 조회 | GET | /api/v1/auctions/{auctionId} | 불필요 | 200 응답 / title·startingPrice·buyoutPrice·currentPrice·status·endedAt / 카드·판매자 정보 포함 | ⬜ |
| TC-AUCTION-005 | 경매 | 중 | 내 경매 목록 조회 | GET | /api/v1/auctions/me | 필요 | 200 응답 / 본인 등록 경매만 반환 | ⬜ |
| TC-AUCTION-006 | 경매 | 중 | 경매 수정 | PATCH | /api/v1/auctions/{auctionId} | 필요 | 200 응답 / 수정 필드 반영 / 타인 수정 시도→403 | ⬜ |
| TC-AUCTION-007 | 입찰 | 최상 | 입찰 생성 | POST | /api/v1/auctions/{auctionId}/bids | 필요 | 201 응답 / currentPrice 업데이트 / 판매자 본인 입찰→403 / 현재가 이하 금액→400 / 동시성 처리 확인 | ⬜ |
| TC-AUCTION-008 | 입찰 | 중 | 입찰 내역 조회 | GET | /api/v1/auctions/{auctionId}/bids | 불필요 | 200 응답 / bidderId·bidAmount·createdAt / 금액 내림차순 정렬 | ⬜ |
| TC-AUCTION-009 | 입찰 | 중 | 내 입찰 목록 조회 | GET | /api/v1/bids/me | 필요 | 200 응답 / WINNING·LOSING·WON status 필터 동작 | ⬜ |
| TC-AUCTION-010 | 경매 | 최상 | 즉시구매 (Buyout) | POST | /api/v1/auctions/{auctionId}/buyout | 필요 | 201 응답 / 주문 자동 생성 / 경매 status: ENDED / 완료된 경매 재시도→409 | ⬜ |
| TC-AUCTION-011 | 경매 | 상 | 경매 취소 (판매자) | PATCH | /api/v1/auctions/{auctionId}/cancel | 필요 | 200 응답 / 경매 status: CANCELLED / 입찰 내역 있는 경우 취소 가능 여부 확인 | ⬜ |
| TC-ORDER-001 | 주문 | 상 | 내 주문 목록 조회 (필터) | GET | /api/v1/orders/me | 필요 | 200 응답 / PENDING_PAYMENT·PAYMENT_COMPLETED·CANCELLED·COMPLETED status 필터 / orderId·finalPrice·paymentDeadline 포함 | ⬜ |
| TC-ORDER-002 | 주문 | 상 | 주문 상세 조회 | GET | /api/v1/orders/{orderUid} | 필요 | 200 응답 / 주문 상세 전체 필드 / 타인 주문 조회→403 | ⬜ |
| TC-ORDER-003 | 주문 | 상 | 주문 취소 | PATCH | /api/v1/orders/{orderUid}/cancel | 필요 | 200 응답 / status: CANCELLED / 결제 완료 후 취소 시 결제 취소 연동 확인 | ⬜ |
| TC-PAYMENT-001 | 결제 | 최상 | 결제 요청 생성 | POST | /api/v1/payments | 필요 | 201 응답 / paymentUid 반환 / amount가 주문 금액과 일치 / 이미 결제된 주문 재시도→409 | ⬜ |
| TC-PAYMENT-002 | 결제 | 최상 | 결제 확인 (금액 검증 포함) | PATCH | /api/v1/payments/{paymentUid} | 필요 | 200 응답 / 결제 status: COMPLETED / 주문 status: PAYMENT_COMPLETED / 정산 자동 생성 / 금액 불일치→결제 취소 | ⬜ |
| TC-PAYMENT-003 | 결제 | 중 | 결제 상세 조회 | GET | /api/v1/payments/{paymentUid} | 필요 | 200 응답 / paymentUid·amount·status·pgProvider·paymentKey / 타인 결제→403 | ⬜ |
| TC-PAYMENT-004 | 결제 | 최상 | PortOne 웹훅 처리 (HMAC 검증) | POST | /api/v1/payments/webhook | 불필요 | 유효 HMAC→200 + 상태 업데이트 / 잘못된 HMAC→401 / 중복 수신→멱등성 처리 | ⬜ |
| TC-REFUND-001 | 환불 | 최상 | 환불 요청 | POST | /api/v1/refunds | 필요 | 201 응답 / status: PENDING / refundId 반환 / 결제 미완료 주문→400 | ⬜ |
| TC-REFUND-002 | 환불 | 중 | 내 환불 목록 조회 (필터) | GET | /api/v1/refunds/me | 필요 | 200 응답 / PENDING·APPROVED·REJECTED status 필터 / 본인 환불만 반환 | ⬜ |
| TC-REFUND-003 | 환불 | 중 | 환불 상세 조회 | GET | /api/v1/refunds/{refundId} | 필요 | 200 응답 / reason·rejectionReason 포함 / 타인 환불→403 | ⬜ |
| TC-SETTLEMENT-001 | 정산 | 상 | 내 정산 목록 조회 | GET | /api/v1/settlements/me | 필요 | 200 응답 / settlementUid·settlementAmount·status 포함 | ⬜ |
| TC-SETTLEMENT-002 | 정산 | 중 | 정산 상세 조회 | GET | /api/v1/settlements/{settlementUid} | 필요 | 200 응답 / settlementAmount 낙찰가 기반 확인 / status: PENDING | ⬜ |
| TC-FREE-001 | 커뮤니티 | 중 | 자유게시글 목록 조회 | GET | /api/v1/posts/free | 불필요 | 200 응답 / 페이지네이션 / keyword 검색 / title·viewCount·createdAt·author 포함 | ⬜ |
| TC-FREE-002 | 커뮤니티 | 중 | 인기 자유게시글 조회 | GET | /api/v1/posts/free/popular | 불필요 | 200 응답 / 조회수/좋아요 기준 정렬 확인 | ⬜ |
| TC-FREE-003 | 커뮤니티 | 중 | 자유게시글 작성 | POST | /api/v1/posts/free | 필요 | 201 응답 / freePostId 반환 / 인증 없이→401 | ⬜ |
| TC-FREE-004 | 커뮤니티 | 중 | 자유게시글 상세 조회 | GET | /api/v1/posts/free/{freePostId} | 불필요 | 200 응답 / 조회 시 viewCount 증가 / 존재하지 않는 ID→404 | ⬜ |
| TC-FREE-005 | 커뮤니티 | 중 | 자유게시글 수정 | PATCH | /api/v1/posts/free/{freePostId} | 필요 | 200 응답 / 수정 내용 반영 / 타인 수정→403 | ⬜ |
| TC-FREE-006 | 커뮤니티 | 중 | 자유게시글 삭제 | DELETE | /api/v1/posts/free/{freePostId} | 필요 | 200 응답 / 삭제 후 재조회→404 / 타인 삭제→403 | ⬜ |
| TC-FREE-007 | 커뮤니티 | 중 | 내 자유게시글 목록 | GET | /api/v1/posts/free/me | 필요 | 200 응답 / 본인 게시글만 반환 | ⬜ |
| TC-COMMENT-001 | 커뮤니티 | 중 | 댓글 목록 조회 | GET | /api/v1/comments | 불필요 | 200 응답 / author·content·createdAt / 대댓글(parentCommentId) 구조 확인 | ⬜ |
| TC-COMMENT-002 | 커뮤니티 | 중 | 댓글 작성 | POST | /api/v1/comments | 필요 | 201 응답 / commentId 반환 | ⬜ |
| TC-COMMENT-003 | 커뮤니티 | 중 | 대댓글 작성 | POST | /api/v1/comments | 필요 | 201 응답 / parentCommentId 연결 / 부모 댓글과 대댓글 조회 확인 | ⬜ |
| TC-COMMENT-004 | 커뮤니티 | 중 | 댓글 수정/삭제 | PATCH / DELETE | /api/v1/comments/{commentId} | 필요 | 수정→200·내용 반영 / 삭제→200·목록 미노출 / 타인 수정·삭제→403 | ⬜ |
| TC-TRADE-001 | 커뮤니티 | 중 | 거래게시글 목록 조회 | GET | /api/v1/posts/trade | 불필요 | 200 응답 / keyword·minPrice·maxPrice·cardId 필터 / 페이지네이션 | ⬜ |
| TC-TRADE-002 | 커뮤니티 | 중 | 거래게시글 작성 | POST | /api/v1/posts/trade | 필요 | 201 응답 / tradePostId 반환 | ⬜ |
| TC-TRADE-003 | 커뮤니티 | 중 | 거래게시글 상세/수정/삭제 | GET / PATCH / DELETE | /api/v1/posts/trade/{tradePostId} | 일부 | 상세→200·전체 필드 / 수정·삭제→본인만 가능 | ⬜ |
| TC-LIKE-001 | 커뮤니티 | 중 | 좋아요 토글 | POST | /api/v1/likes | 필요 | 첫 요청→좋아요 추가(201) / 재요청→좋아요 취소(200) | ⬜ |
| TC-LIKE-002 | 커뮤니티 | 중 | 내 좋아요 목록 | GET | /api/v1/likes/me | 필요 | 200 응답 / 좋아요한 경매 목록 / 커서 기반 페이지네이션 | ⬜ |
| TC-CHAT-001 | 채팅 | 상 | 채팅방 생성 | POST | /api/v1/chats | 필요 | 201 응답 / chatId 반환 / 동일 상대 중복 생성→기존 채팅방 반환(멱등성) | ⬜ |
| TC-CHAT-002 | 채팅 | 상 | 내 채팅방 목록 조회 | GET | /api/v1/chats/me | 필요 | 200 응답 / chatId·opponent·lastMessage·lastMessageAt·unreadCount / 최신 시간순 정렬 | ⬜ |
| TC-CHAT-003 | 채팅 | 상 | 채팅 메시지 목록 조회 | GET | /api/v1/chats/{chatId}/messages | 필요 | 200 응답 / messageId·userId·message·createdAt / 최신→구 정렬 / 비참여자→403 | ⬜ |
| TC-CHAT-004 | 채팅 | 중 | 채팅 읽음 처리 (REST) | PATCH | /api/v1/chats/{chatId}/read | 필요 | 200 응답 / unreadCount 0으로 변경 / 목록 조회 시 반영 | ⬜ |
| TC-CHAT-005 | 채팅 | 중 | 채팅방 나가기 | DELETE | /api/v1/chats/{chatId} | 필요 | 200 응답 / 나간 채팅방 목록 미노출 / 상대방은 여전히 존재 | ⬜ |
| TC-CHAT-WS-001 | 채팅 | 상 | WebSocket 연결 | WS | /ws/chat | 필요 | 연결 성공 / 인증 없이 연결 시 거부 | ⬜ |
| TC-CHAT-WS-002 | 채팅 | 상 | 채팅 구독 (STOMP) | SUB | /sub/chat/{chatId} | 필요 | 구독 성공 / 비참여자 구독 시 거부 | ⬜ |
| TC-CHAT-WS-003 | 채팅 | 상 | 메시지 전송 (실시간) | SEND | /chat/{chatId} | 필요 | 상대방 구독 채널에 메시지 도착 / REST 메시지 목록에도 포함 / lastMessage 업데이트 | ⬜ |
| TC-CHAT-WS-004 | 채팅 | 중 | 읽음 처리 (WebSocket) | SEND | /chat/{chatId}/read | 필요 | unreadCount 0 확인 / 상대방에게 읽음 이벤트 전달 여부 | ⬜ |
| TC-NOTI-001 | 알림 | 중 | 알림 목록 조회 | GET | /api/v1/notifications | 필요 | 200 응답 / 커서 기반 페이지네이션 / notificationId·type·isRead·createdAt / 최신순 | ⬜ |
| TC-NOTI-002 | 알림 | 중 | 알림 단건 읽음 처리 | PATCH | /api/v1/notifications/{notificationId}/read | 필요 | 200 응답 / isRead: true 변경 / 목록 재조회 시 반영 | ⬜ |
| TC-NOTI-003 | 알림 | 중 | 전체 알림 읽음 처리 | PATCH | /api/v1/notifications/read-all | 필요 | 200 응답 / 모든 알림 isRead: true | ⬜ |
| TC-NOTI-004 | 알림 | 낮음 | 알림 단건 삭제 | DELETE | /api/v1/notifications/{notificationId} | 필요 | 200 응답 / 삭제 후 목록 미노출 / 타인 알림→403 | ⬜ |
| TC-NOTI-005 | 알림 | 낮음 | 알림 전체 삭제 | DELETE | /api/v1/notifications | 필요 | 200 응답 / 이후 목록 조회 시 빈 배열 | ⬜ |
| TC-NOTI-006 | 알림 | 상 | 실제 이벤트 발생 후 알림 확인 | 복합 | 복합 | 필요 | 입찰 후 판매자 알림 도착 / 알림 type 올바른 값 / Kafka 처리 후 3초 내 도착 확인 | ⬜ |
| TC-ADMIN-CARD-001 | 어드민 | 최상 | 카드 신청 목록 조회 | GET | /api/v1/admin/cards/requests | ADMIN | 200 응답 / PENDING 카드 목록 / cardId·userId·imageUrl·grade·status / 일반 사용자→403 | ⬜ |
| TC-ADMIN-CARD-002 | 어드민 | 최상 | 카드 승인 + 알림 확인 | PATCH | /api/v1/admin/cards/{cardId}/approve | ADMIN | 200 응답 / status: APPROVED / 공개 목록 노출 / 신청자 알림 발송 / 재승인→400 | ⬜ |
| TC-ADMIN-CARD-003 | 어드민 | 최상 | 카드 거절 + 사유 저장 + 알림 | PATCH | /api/v1/admin/cards/{cardId}/reject | ADMIN | 200 응답 / status: REJECTED / rejectReason 저장 / 신청자 알림 / 공개 목록 미노출 / 사유 없이→400 | ⬜ |
| TC-ADMIN-CARD-004 | 어드민 | 중 | 카드 정보 수정 (어드민) | PATCH | /api/v1/admin/cards/{cardId} | ADMIN | 200 응답 / 수정 필드 반영 | ⬜ |
| TC-ADMIN-CARD-005 | 어드민 | 중 | 카드 삭제 (어드민) | DELETE | /api/v1/admin/cards/{cardId} | ADMIN | 200 응답 / 삭제 후 조회→404 / 진행 중 경매 있는 경우 처리 확인 | ⬜ |
| TC-ADMIN-CARD-006 | 어드민 | 중 | TCGdex 카드 동기화 | POST | /api/v1/admin/cards/sync | ADMIN | 202 Accepted(비동기) / 동기화 후 새 카드 확인 / 중복→upsert 처리 | ⬜ |
| TC-ADMIN-CARD-007 | 어드민 | 낮음 | 카드 이미지 S3 마이그레이션 | POST | /api/v1/admin/cards/migrate-images | ADMIN | 202 Accepted(비동기) / 마이그레이션 후 S3 URL 변경 확인 | ⬜ |
| TC-ADMIN-AUCTION-001 | 어드민 | 상 | 경매 목록 조회 (어드민) | GET | /api/v1/admin/auctions | ADMIN | 200 응답 / status 필터(PENDING·ACTIVE 등) | ⬜ |
| TC-ADMIN-AUCTION-002 | 어드민 | 최상 | 경매 검수 통과 + 입찰 가능 확인 | PATCH | /api/v1/admin/auctions/{auctionId}/inspection | ADMIN | 200 응답 / status: ACTIVE / inspectedAt·inspectedBy 저장 / 입찰 가능 확인 / 공개 목록 노출 | ⬜ |
| TC-ADMIN-AUCTION-003 | 어드민 | 최상 | 경매 검수 거절 + 알림 | PATCH | /api/v1/admin/auctions/{auctionId}/inspection | ADMIN | 200 응답 / status: CANCELLED / 판매자 알림 발송 / 거절된 경매 입찰 불가 | ⬜ |
| TC-ADMIN-AUCTION-004 | 어드민 | 상 | 경매 강제 취소 (어드민) | PATCH | /api/v1/admin/auctions/{auctionId}/cancel | ADMIN | 200 응답 / status: CANCELLED / 기존 입찰자 취소 알림 / 입찰 금액 환불 처리 | ⬜ |
| TC-ADMIN-USER-001 | 어드민 | 상 | 사용자 목록 조회 | GET | /api/v1/admin/users | ADMIN | 200 응답 / keyword 검색 / isBidBlocked 필터 / userId·email·isBidBlocked·unpaidStrike 포함 | ⬜ |
| TC-ADMIN-USER-002 | 어드민 | 상 | 사용자 입찰 차단 (**엔드포인트 노출 여부 확인 필요**) | PATCH | /api/v1/admin/users/{userId}/block (미노출) | ADMIN | 서비스 구현됨(toggleBidBlock) / 컨트롤러 엔드포인트 존재 여부 확인 / 차단 후 입찰→403 | ⬜ |
| TC-ADMIN-REFUND-001 | 어드민 | 최상 | 환불 목록 조회 | GET | /api/v1/admin/refunds | ADMIN | 200 응답 / PENDING·APPROVED·REJECTED status 필터 / refundId·orderId·userId·reason 포함 | ⬜ |
| TC-ADMIN-REFUND-002 | 어드민 | 최상 | 환불 승인 + PortOne 자동 환불 | PATCH | /api/v1/admin/refunds/{refundId}/approve | ADMIN | 200 응답 / status: APPROVED / PortOne 실제 취소 처리 / 구매자 알림 / 주문·정산 상태 변경 확인 | ⬜ |
| TC-ADMIN-REFUND-003 | 어드민 | 최상 | 환불 거절 + 사유 + 알림 | PATCH | /api/v1/admin/refunds/{refundId}/reject | ADMIN | 200 응답 / status: REJECTED / rejectionReason 저장 / 구매자 알림 / 사유 없이→400 | ⬜ |
| TC-ADMIN-SETTLEMENT-001 | 어드민 | 상 | 정산 목록 조회 | GET | /api/v1/admin/settlements | ADMIN | 200 응답 / PENDING·COMPLETED status 필터 / settlementUid·amount·status 포함 | ⬜ |
| TC-ADMIN-SETTLEMENT-002 | 어드민 | 최상 | 정산 완료 처리 + 알림 | PATCH | /api/v1/admin/settlements/{settlementUid}/complete | ADMIN | 200 응답 / status: COMPLETED / 판매자 알림 발송 / 판매자 목록 조회 시 반영 / 이미 완료→409 | ⬜ |
| TC-ADMIN-ORDER-001 | 어드민 | 상 | 주문 목록 조회 | GET | /api/v1/admin/orders | ADMIN | 200 응답 / status·buyerId 필터 | ⬜ |
| TC-ADMIN-REF-001 | 어드민 | 중 | 포켓몬 CRUD | GET/POST/PATCH/DELETE | /api/v1/admin/pokemon | ADMIN | 생성 후 카드 등록 시 선택 가능 / 삭제 시 연관 카드 처리 확인 | ⬜ |
| TC-ADMIN-REF-002 | 어드민 | 중 | 시리즈 CRUD | GET/POST/PATCH/DELETE | /api/v1/admin/series | ADMIN | 생성 후 공개 API에 노출 / 세트 연결된 시리즈 삭제 처리 | ⬜ |
| TC-ADMIN-REF-003 | 어드민 | 중 | 세트 CRUD | GET/POST/PATCH/DELETE | /api/v1/admin/sets | ADMIN | 생성 후 공개 API에 노출 / 카드 연결된 세트 삭제 처리 | ⬜ |
| TC-ADMIN-AI-001 | 어드민 | 중 | AI 벡터스토어 전체 리인덱싱 | POST | /api/v1/admin/ai/reindex | ADMIN | 202 Accepted(비동기) / 리인덱싱 후 AI RAG 응답 품질 확인 | ⬜ |
| TC-ADMIN-ES-001 | 어드민 | 상 | DB → ES 마이그레이션 | POST | /api/v1/admin/es-migrate | ADMIN | 200 응답 / 마이그레이션 후 카드·경매 검색 동작 확인 | ⬜ |
| TC-ADMIN-ES-002 | 어드민 | 중 | ES 인덱스 별칭 초기 설정 | POST | /api/v1/admin/es-alias-setup | ADMIN | 200 응답 / 이미 설정된 경우 멱등성 처리 | ⬜ |
| TC-ADMIN-ES-003 | 어드민 | 중 | 제로다운타임 리인덱싱 | POST | /api/v1/admin/es-reindex | ADMIN | 202 Accepted / 진행 중 검색 API 정상 동작(무중단) / 완료 후 alias 전환 | ⬜ |
| TC-INTERNAL-001 | 내부 | 중 | 일일 통계 조회 | GET | /internal/stats/daily | X-Internal-Token | 200 응답 / 일일 통계 데이터(신규가입·경매수·거래금액) / 토큰 없이→401 | ⬜ |
| TC-ADMIN-AUTH-001 | 어드민 | 최상 | 어드민 API 권한 차단 검증 | GET | /api/v1/admin/** | 일반 사용자 | 일반 사용자 토큰→403 / 비로그인→401 | ⬜ |
| TC-AI-001 | AI | 중 | 카드 AI 분석 조회 (캐시) | GET | /api/ai/cards/{cardId}/analysis | 필요 | 200 응답 / analysis·grade·rarity·condition 포함 / 연속 호출 시 빠른 응답(캐시 확인) | ⬜ |
| TC-AI-002 | AI | 중 | 카드 AI 분석 강제 재실행 | POST | /api/ai/cards/{cardId}/analysis | 필요 | 200 응답 / 새 분석 결과 반환(기존 캐시 무효화) / Circuit Breaker 동작 확인 | ⬜ |
| TC-AI-003 | AI | 중 | AI 어시스턴트 채팅 | POST | /api/ai/assistant/chat | 필요 | 200 응답 / 자연어 응답 / RAG 컨텍스트 활용(구체적 정보 포함) / sessionId 유지 시 문맥 연속성 | ⬜ |
| TC-AI-004 | AI | 중 | AI 스트리밍 응답 (SSE) | GET | /api/ai/assistant/stream | 필요 | text/event-stream Content-Type / 실시간 스트리밍 응답 / 스트림 완료 신호 수신 | ⬜ |
| TC-AI-005 | AI | 중 | 대화 세션 연속성 | POST | /api/ai/assistant/chat | 필요 | 동일 sessionId 2번째 질문에서 이전 문맥 참조 / 다른 sessionId는 독립 처리 | ⬜ |
| TC-AI-006 | AI | 중 | AI API 장애 Circuit Breaker | POST | /api/ai/cards/{cardId}/analysis | 필요 | AI API 불가 시 적절한 에러(500/503) / CB OPEN 상태 빠른 실패 / fallback 동작 | ⬜ |

---

## E2E 통합 시나리오

| E2E ID | 제목 | 참여자 | 주요 단계 | 최종 검증 상태 | 결과 |
|--------|------|--------|-----------|----------------|------|
| E2E-001 | 즉시구매 → 결제 → 정산 전체 플로우 | User A(판매자), User B(구매자), Admin | 회원가입→로그인→카드 신청→**카드 승인(Admin)**→경매 생성→**경매 검수 통과(Admin)**→즉시구매→결제 요청→PortOne 결제→결제 확인→**정산 완료 처리(Admin)**→알림 확인 | 경매: ENDED / 주문: PAYMENT_COMPLETED / 결제: COMPLETED / 정산: COMPLETED / User A 알림 2건(결제완료·정산완료) | ⬜ |
| E2E-002 | 경매 입찰 → 낙찰 → 결제 → 환불 | User A(판매자), User B(구매자), Admin | E2E-001 Step1~7 동일→입찰→경매 종료→낙찰 확인→결제 완료→환불 요청→**환불 승인(Admin)**→PortOne 자동 환불→알림 확인 | 환불: APPROVED / User B 알림: 환불완료 | ⬜ |
| E2E-003 | 거래게시글 → 채팅 연계 | User A, User B | 거래게시글 작성→User B 조회→채팅방 생성→WebSocket 메시지 전송→User A 알림 수신→읽음 처리→unreadCount 0 확인 | 채팅 메시지 정상 전달 / unreadCount 0 | ⬜ |
| E2E-004 | 카드 거절 → 재신청 → 승인 | User A, Admin | 카드 신청→**카드 거절(Admin)**→거절 알림 수신→my-requests에서 사유 확인→새 이미지로 재신청→**재승인(Admin)** | 최종 카드 status: APPROVED | ⬜ |

---

## 승인 플로우 상태 전이 요약

| 엔티티 | 초기 상태 | 어드민 승인 | 어드민 거절/취소 |
|--------|-----------|-------------|-----------------|
| 카드 | PENDING | APPROVED | REJECTED |
| 경매 | PENDING | ACTIVE | CANCELLED |
| 환불 | PENDING | APPROVED (→PortOne 환불) | REJECTED |
| 정산 | PENDING | COMPLETED | - |

---

## 공통 에러 코드

| HTTP 상태 | 의미 | 주요 발생 케이스 |
|-----------|------|----------------|
| 400 | 잘못된 요청 | 유효성 검사 실패, 사유 누락 |
| 401 | 인증 실패 | 토큰 없음/만료/블랙리스트 |
| 403 | 권한 부족 | 일반 사용자 admin API 접근, 타인 리소스 접근 |
| 404 | 리소스 없음 | 잘못된 ID |
| 409 | 충돌 | 중복 가입, 이미 처리된 요청 |
| 429 | Rate Limit 초과 | 로그인 10회, 회원가입 5회, 입찰 30회 등 |
| 500/503 | 서버 오류 | AI API 장애, 외부 서비스 오류 |
