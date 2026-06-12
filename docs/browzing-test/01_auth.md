# 01. 인증 (Auth) 테스트 시나리오

## TC-AUTH-001: 회원가입 성공

**우선순위**: 최상  
**관련 API**: `POST /api/v1/auth/signup`

### 요청
```json
POST /api/v1/auth/signup
Content-Type: application/json

{
  "email": "user_a@test.com",
  "password": "Test1234!",
  "nickname": "테스트유저A",
  "phone": "010-1234-5678",
  "address": "서울시 강남구 테스트로 1"
}
```

### 예상 응답
- HTTP 201 Created
- 응답 바디에 사용자 정보 포함

### 확인 항목
- [ ] 201 응답 코드
- [ ] 응답에 userId, email, nickname 포함
- [ ] 동일 이메일로 재가입 시 409 반환 확인

---

## TC-AUTH-002: 회원가입 유효성 검사 실패

**우선순위**: 상  
**관련 API**: `POST /api/v1/auth/signup`

### 테스트 케이스

| 케이스 | 변경 필드 | 입력값 | 예상 결과 |
|--------|-----------|--------|-----------|
| 이메일 형식 오류 | email | "not-email" | 400 Bad Request |
| 비밀번호 미충족 | password | "123" | 400 Bad Request |
| 닉네임 빈값 | nickname | "" | 400 Bad Request |
| 전화번호 형식 오류 | phone | "01012345678" | 400 Bad Request (형식: 010-xxxx-xxxx) |

---

## TC-AUTH-003: 로그인 성공

**우선순위**: 최상  
**관련 API**: `POST /api/v1/auth/login`

### 요청
```json
POST /api/v1/auth/login
Content-Type: application/json

{
  "email": "user_a@test.com",
  "password": "Test1234!"
}
```

### 예상 응답
- HTTP 200 OK
- `accessToken`, `refreshToken` 발급

### 확인 항목
- [ ] 200 응답 코드
- [ ] accessToken 존재 및 JWT 형식 (`.` 으로 구분된 3파트)
- [ ] refreshToken 존재
- [ ] 발급된 accessToken으로 인증 필요 API 호출 성공 여부

### 저장
```
ACCESS_TOKEN_A = 응답의 accessToken
REFRESH_TOKEN_A = 응답의 refreshToken
```

---

## TC-AUTH-004: 로그인 실패 - 잘못된 비밀번호

**우선순위**: 상  
**관련 API**: `POST /api/v1/auth/login`

### 요청
```json
{
  "email": "user_a@test.com",
  "password": "WrongPassword!"
}
```

### 확인 항목
- [ ] 400 Bad Request (USER_INFO_MISMATCH)
- [ ] 에러 메시지 존재

---

## TC-AUTH-005: 토큰 갱신 (Reissue)

**우선순위**: 상  
**관련 API**: `POST /api/v1/auth/reissue`

### 사전 조건
- 유효한 refreshToken 보유 (TC-AUTH-003 이후)

### 요청
```json
POST /api/v1/auth/reissue
Content-Type: application/json

{
  "refreshToken": "{REFRESH_TOKEN_A}"
}
```

### 확인 항목
- [ ] 200 응답
- [ ] 새로운 accessToken 발급
- [ ] 만료된 refreshToken으로 재시도 시 401 반환

---

## TC-AUTH-006: 로그아웃

**우선순위**: 상  
**관련 API**: `POST /api/v1/auth/logout`

### 요청
```
POST /api/v1/auth/logout
Authorization: Bearer {ACCESS_TOKEN_A}
```

### 확인 항목
- [ ] 200 응답
- [ ] 로그아웃 후 기존 accessToken으로 인증 필요 API 호출 시 401 반환 (블랙리스트 처리)

---

## TC-AUTH-007: Rate Limit 검증

**우선순위**: 중  
**관련 API**: `POST /api/v1/auth/login`

### 요청 (10회 반복)

```json
POST /api/v1/auth/login
Content-Type: application/json

{
  "email": "user_a@test.com",
  "password": "WrongPassword!"
}
```

### 예상 응답 (11번째 요청부터)
- HTTP 429 Too Many Requests

### 확인 항목
- [ ] 로그인 10회 초과 시 429 (Too Many Requests) 반환
- [ ] 회원가입 5회 초과 시 429 반환
- [ ] Rate Limit 초기화(1분) 후 정상 응답 확인

---

## 체크리스트 요약

| TC | 설명 | 결과 |
|----|------|------|
| TC-AUTH-001 | 정상 회원가입 | ⬜ |
| TC-AUTH-002 | 유효성 검사 실패 | ⬜ |
| TC-AUTH-003 | 정상 로그인 + 토큰 발급 | ⬜ |
| TC-AUTH-004 | 잘못된 비밀번호 로그인 | ⬜ |
| TC-AUTH-005 | 토큰 갱신 | ⬜ |
| TC-AUTH-006 | 로그아웃 + 토큰 블랙리스트 | ⬜ |
| TC-AUTH-007 | Rate Limit | ⬜ |
