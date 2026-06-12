# 11. 사용자 프로필 / 빌링키 테스트 시나리오

## TC-USER-001: 내 프로필 조회

**우선순위**: 상  
**관련 API**: `GET /api/v1/users/me`  
**인증 필요**: 있음

### 요청
```
GET /api/v1/users/me
Authorization: Bearer {ACCESS_TOKEN_A}
```

### 확인 항목
- [ ] 200 응답
- [ ] 프로필 정보 (userId, email, nickname, phone, address, userRole)
- [ ] 민감 정보(password) 미노출 확인
- [ ] 인증 없이 요청 시 401

---

## TC-USER-002: 내 프로필 수정

**우선순위**: 중  
**관련 API**: `PATCH /api/v1/users/me`  
**인증 필요**: 있음

### 요청
```json
PATCH /api/v1/users/me
Authorization: Bearer {ACCESS_TOKEN_A}

{
  "nickname": "새닉네임",
  "phone": "010-9999-8888",
  "address": "서울시 마포구 새주소로 1"
}
```

### 확인 항목
- [ ] 200 응답
- [ ] 수정된 필드 반영 확인
- [ ] 중복 닉네임 사용 시 409

---

## TC-USER-003: 빌링키 등록

**우선순위**: 상  
**관련 API**: `POST /api/v1/users/me/billing-key`  
**인증 필요**: 있음

### 사전 조건
- PortOne에서 빌링키 발급 완료 (클라이언트 측)

### 요청
```json
POST /api/v1/users/me/billing-key
Authorization: Bearer {ACCESS_TOKEN_B}

{
  "billingKey": "billing_xxxx"
}
```

### 확인 항목
- [ ] 200 응답
- [ ] 사용자 계정에 빌링키 연결 확인
- [ ] 빌링키 등록 후 자동결제 가능 여부

---

## TC-USER-004: 빌링키 수정

**관련 API**: `PUT /api/v1/users/me/billing-key`

### 확인 항목
- [ ] 200 응답
- [ ] 기존 빌링키 교체 확인

---

## TC-USER-005: 빌링키 삭제

**관련 API**: `DELETE /api/v1/users/me/billing-key`

### 확인 항목
- [ ] 200 응답
- [ ] 삭제 후 자동결제 불가 확인

---

## 체크리스트 요약

| TC | 설명 | 결과 |
|----|------|------|
| TC-USER-001 | 프로필 조회 | ⬜ |
| TC-USER-002 | 프로필 수정 | ⬜ |
| TC-USER-003 | 빌링키 등록 | ⬜ |
| TC-USER-004 | 빌링키 수정 | ⬜ |
| TC-USER-005 | 빌링키 삭제 | ⬜ |
