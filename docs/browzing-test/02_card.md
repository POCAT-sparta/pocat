# 02. 카드 (Card) 테스트 시나리오

## TC-CARD-001: 카드 목록 조회 (공개)

**우선순위**: 상  
**관련 API**: `GET /api/v1/cards`  
**인증 필요**: 없음

### 요청
```
GET /api/v1/cards?page=0&size=20
```

### 필터 파라미터 테스트

| 파라미터 | 예시 값 | 확인 항목 |
|---------|---------|-----------|
| keyword | `피카츄` | 카드명에 키워드 포함 결과만 반환 |
| seriesId | `{seriesId}` | 해당 시리즈 카드만 반환 |
| setId | `{setId}` | 해당 세트 카드만 반환 |
| grade | `PSA_10` | 해당 등급 카드만 반환 |
| status | `APPROVED` | 승인된 카드만 반환 |

### 확인 항목
- [ ] 200 응답
- [ ] 페이지네이션 (content, totalElements, totalPages)
- [ ] 각 카드에 id, name, imageUrl, grade, status 포함

---

## TC-CARD-002: 카드 상세 조회

**우선순위**: 상  
**관련 API**: `GET /api/v1/cards/{cardId}`

### 요청
```
GET /api/v1/cards/{cardId}
```

### 확인 항목
- [ ] 200 응답
- [ ] 카드 기본 정보 (name, series, set, pokemon, grade, rarity, status)
- [ ] 이미지 URL 유효성
- [ ] 존재하지 않는 ID 조회 시 404

---

## TC-CARD-003: 카드 경매 조회

**우선순위**: 상  
**관련 API**: `GET /api/v1/cards/{cardId}/auctions`

### 요청
```
GET /api/v1/cards/{cardId}/auctions
```

### 확인 항목
- [ ] 200 응답
- [ ] 해당 카드의 진행 중인 경매 목록 반환
- [ ] 각 경매에 auctionId, title, currentPrice, endedAt 포함

---

## TC-CARD-004: 카드 평균 가격 조회

**우선순위**: 중  
**관련 API**: `GET /api/v1/cards/{cardId}/average-price`

### 확인 항목
- [ ] 200 응답
- [ ] averagePrice 필드 존재
- [ ] 거래 내역 없는 카드의 경우 0 또는 null 처리 확인

---

## TC-CARD-005: 카드 등록 신청 (이미지 포함)

**우선순위**: 상  
**관련 API**: `POST /api/v1/cards/upload`  
**인증 필요**: 있음 (일반 사용자)

### 요청
```
POST /api/v1/cards/upload
Authorization: Bearer {ACCESS_TOKEN_A}
Content-Type: multipart/form-data

image: (실제 이미지 파일, jpg/png)
data: {
  "pokemonId": {pokemonId},
  "seriesId": {seriesId},
  "setId": {setId},
  "cardNumber": "001",
  "grade": "PSA_10",
  "rarity": "RARE",
  "category": "NORMAL"
}
```

### 확인 항목
- [ ] 201 응답
- [ ] 카드 상태가 `PENDING` (심사 대기)
- [ ] S3 업로드된 imageUrl 반환
- [ ] 인증 없이 요청 시 401

---

## TC-CARD-006: 내 카드 신청 목록 조회

**우선순위**: 중  
**관련 API**: `GET /api/v1/cards/my-requests`  
**인증 필요**: 있음

### 요청
```
GET /api/v1/cards/my-requests
Authorization: Bearer {ACCESS_TOKEN_A}
```

### 확인 항목
- [ ] 200 응답
- [ ] 본인이 신청한 카드 목록만 반환
- [ ] 각 카드의 status(PENDING/APPROVED/REJECTED) 표시

---

## TC-CARD-007: 시리즈 목록 조회

**우선순위**: 중  
**관련 API**: `GET /api/v1/series`

### 확인 항목
- [ ] 200 응답
- [ ] 시리즈 목록 (id, name, nameKo) 반환

---

## TC-CARD-008: 세트 목록 조회

**우선순위**: 중  
**관련 API**: `GET /api/v1/sets`

### 요청
```
GET /api/v1/sets
GET /api/v1/sets?seriesId={seriesId}  ← 시리즈 필터
```

### 확인 항목
- [ ] 200 응답
- [ ] seriesId 필터 동작 확인

---

## 체크리스트 요약

| TC | 설명 | 결과 |
|----|------|------|
| TC-CARD-001 | 카드 목록 조회 (필터 포함) | ⬜ |
| TC-CARD-002 | 카드 상세 조회 | ⬜ |
| TC-CARD-003 | 카드 경매 목록 | ⬜ |
| TC-CARD-004 | 카드 평균 가격 | ⬜ |
| TC-CARD-005 | 카드 등록 신청 (이미지 업로드) | ⬜ |
| TC-CARD-006 | 내 신청 목록 | ⬜ |
| TC-CARD-007 | 시리즈 목록 | ⬜ |
| TC-CARD-008 | 세트 목록 (필터 포함) | ⬜ |
