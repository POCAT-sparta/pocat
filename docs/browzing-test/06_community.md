# 06. 커뮤니티 (Community) 테스트 시나리오

## 자유게시판 (Free Post)

### TC-FREE-001: 자유게시글 목록 조회

**우선순위**: 중  
**관련 API**: `GET /api/v1/posts/free`  
**인증 필요**: 없음

### 요청
```
GET /api/v1/posts/free?page=0&size=20
GET /api/v1/posts/free?keyword=피카츄
```

### 확인 항목
- [ ] 200 응답
- [ ] 페이지네이션
- [ ] keyword 검색 동작
- [ ] 각 게시글에 title, viewCount, createdAt, author 포함

---

### TC-FREE-002: 인기 자유게시글 조회

**우선순위**: 중  
**관련 API**: `GET /api/v1/posts/free/popular`

### 확인 항목
- [ ] 200 응답
- [ ] 조회수/좋아요 기준 정렬 확인

---

### TC-FREE-003: 자유게시글 작성

**우선순위**: 중  
**관련 API**: `POST /api/v1/posts/free`  
**인증 필요**: 있음

### 요청
```json
POST /api/v1/posts/free
Authorization: Bearer {ACCESS_TOKEN_A}

{
  "title": "피카츄 카드 구합니다",
  "content": "PSA 10 등급 피카츄 카드 구매 원합니다."
}
```

### 확인 항목
- [ ] 201 응답
- [ ] freePostId 반환 → 저장: `FREE_POST_ID`
- [ ] 인증 없이 작성 시 401

---

### TC-FREE-004: 자유게시글 상세 조회

**관련 API**: `GET /api/v1/posts/free/{freePostId}`

### 확인 항목
- [ ] 200 응답
- [ ] 상세 조회 시 viewCount 증가 확인
- [ ] 존재하지 않는 ID 조회 시 404

---

### TC-FREE-005: 자유게시글 수정

**관련 API**: `PATCH /api/v1/posts/free/{freePostId}`  
**인증 필요**: 있음 (작성자 본인)

### 확인 항목
- [ ] 200 응답
- [ ] 수정된 내용 반영
- [ ] 다른 사용자가 수정 시도 시 403

---

### TC-FREE-006: 자유게시글 삭제

**관련 API**: `DELETE /api/v1/posts/free/{freePostId}`  
**인증 필요**: 있음 (작성자 본인)

### 확인 항목
- [ ] 200 응답
- [ ] 삭제 후 재조회 시 404
- [ ] 다른 사용자가 삭제 시도 시 403

---

### TC-FREE-007: 내 자유게시글 목록

**관련 API**: `GET /api/v1/posts/free/me`  
**인증 필요**: 있음

### 확인 항목
- [ ] 200 응답
- [ ] 본인 게시글만 반환

---

## 댓글 (Comment)

### TC-COMMENT-001: 댓글 목록 조회

**관련 API**: `GET /api/v1/comments?freePostId={FREE_POST_ID}`

### 확인 항목
- [ ] 200 응답
- [ ] 댓글 목록 (author, content, createdAt)
- [ ] 대댓글(parentCommentId) 구조 확인

---

### TC-COMMENT-002: 댓글 작성

**관련 API**: `POST /api/v1/comments`  
**인증 필요**: 있음

```json
POST /api/v1/comments
Authorization: Bearer {ACCESS_TOKEN_B}

{
  "freePostId": {FREE_POST_ID},
  "content": "좋은 게시글 감사합니다.",
  "parentCommentId": null
}
```

### 확인 항목
- [ ] 201 응답
- [ ] commentId 반환

---

### TC-COMMENT-003: 대댓글 작성

```json
{
  "freePostId": {FREE_POST_ID},
  "content": "감사합니다!",
  "parentCommentId": {COMMENT_ID}
}
```

### 확인 항목
- [ ] 201 응답
- [ ] 부모 댓글과 연결된 대댓글 조회 확인

---

### TC-COMMENT-004: 댓글 수정/삭제

### 확인 항목
- [ ] 수정: 200, 내용 반영
- [ ] 삭제: 200, 삭제 후 목록에 미노출
- [ ] 타인 수정/삭제 시 403

---

## 거래게시판 (Trade Post)

### TC-TRADE-001: 거래게시글 목록 조회

**관련 API**: `GET /api/v1/posts/trade`  
**필터**: keyword, minPrice, maxPrice, cardId

### 확인 항목
- [ ] 200 응답
- [ ] 필터 동작

---

### TC-TRADE-002: 거래게시글 작성

**관련 API**: `POST /api/v1/posts/trade`  
**인증 필요**: 있음

```json
POST /api/v1/posts/trade
Authorization: Bearer {ACCESS_TOKEN_A}

{
  "title": "리자몽 카드 교환 원합니다",
  "description": "피카츄와 교환 원합니다.",
  "price": 30000,
  "wantedCards": ["피카츄", "뮤츠"]
}
```

### 확인 항목
- [ ] 201 응답
- [ ] tradePostId 반환

---

### TC-TRADE-003: 거래게시글 상세/수정/삭제

### 확인 항목
- [ ] 상세 조회: 200, 전체 필드 포함
- [ ] 수정: 본인만 가능
- [ ] 삭제: 본인만 가능

---

## 좋아요 (Like)

### TC-LIKE-001: 좋아요 토글

**관련 API**: `POST /api/v1/likes`  
**인증 필요**: 있음

```json
POST /api/v1/likes
Authorization: Bearer {ACCESS_TOKEN_B}

{
  "auctionId": {AUCTION_ID}
}
```

### 확인 항목
- [ ] 첫 요청: 좋아요 추가 (201)
- [ ] 동일 요청 재전송: 좋아요 취소 (200)

---

### TC-LIKE-002: 내 좋아요 목록

**관련 API**: `GET /api/v1/likes/me`

### 확인 항목
- [ ] 200 응답
- [ ] 좋아요한 경매 목록 반환
- [ ] 커서 기반 페이지네이션

---

## 체크리스트 요약

| TC | 설명 | 결과 |
|----|------|------|
| TC-FREE-001 | 자유게시글 목록 | ⬜ |
| TC-FREE-002 | 인기 게시글 | ⬜ |
| TC-FREE-003 | 게시글 작성 | ⬜ |
| TC-FREE-004 | 게시글 상세 | ⬜ |
| TC-FREE-005 | 게시글 수정 | ⬜ |
| TC-FREE-006 | 게시글 삭제 | ⬜ |
| TC-COMMENT-001 | 댓글 목록 | ⬜ |
| TC-COMMENT-002 | 댓글 작성 | ⬜ |
| TC-COMMENT-003 | 대댓글 작성 | ⬜ |
| TC-COMMENT-004 | 댓글 수정/삭제 | ⬜ |
| TC-TRADE-001 | 거래게시글 목록 | ⬜ |
| TC-TRADE-002 | 거래게시글 작성 | ⬜ |
| TC-TRADE-003 | 거래게시글 상세/수정/삭제 | ⬜ |
| TC-LIKE-001 | 좋아요 토글 | ⬜ |
| TC-LIKE-002 | 내 좋아요 목록 | ⬜ |
