# 07. 채팅 (Chat) 테스트 시나리오

## 사전 조건
- User A, User B 모두 로그인 완료
- WebSocket 테스트 도구 필요 (Postman WebSocket, wscat 등)

---

## REST API 테스트

### TC-CHAT-001: 채팅방 생성

**우선순위**: 상  
**관련 API**: `POST /api/v1/chats`  
**인증 필요**: 있음

### 요청
```json
POST /api/v1/chats
Authorization: Bearer {ACCESS_TOKEN_A}

{
  "targetUserId": {USER_B_ID}
}
```

### 확인 항목
- [ ] 201 응답
- [ ] chatId 반환 → 저장: `CHAT_ID`
- [ ] 동일 대화 상대와 중복 채팅방 생성 시 기존 채팅방 반환 (멱등성)

---

### TC-CHAT-002: 내 채팅방 목록 조회

**우선순위**: 상  
**관련 API**: `GET /api/v1/chats/me`  
**인증 필요**: 있음

### 확인 항목
- [ ] 200 응답
- [ ] 채팅방 목록 (chatId, opponent, lastMessage, lastMessageAt, unreadCount)
- [ ] 최신 메시지 시간순 정렬

---

### TC-CHAT-003: 채팅 메시지 목록 조회

**우선순위**: 상  
**관련 API**: `GET /api/v1/chats/{chatId}/messages`  
**인증 필요**: 있음

### 요청
```
GET /api/v1/chats/{CHAT_ID}/messages?page=0&size=50
```

### 확인 항목
- [ ] 200 응답
- [ ] 메시지 목록 (messageId, userId, message, createdAt)
- [ ] 시간 역순(최신→구) 정렬
- [ ] 해당 채팅방 참여자가 아닌 경우 403

---

### TC-CHAT-004: 채팅 읽음 처리

**우선순위**: 중  
**관련 API**: `PATCH /api/v1/chats/{chatId}/read`  
**인증 필요**: 있음

### 확인 항목
- [ ] 200 응답
- [ ] unreadCount 0으로 변경 확인
- [ ] 목록 조회 시 해당 채팅방 unreadCount 반영

---

### TC-CHAT-005: 채팅방 나가기

**우선순위**: 중  
**관련 API**: `DELETE /api/v1/chats/{chatId}`  
**인증 필요**: 있음

### 확인 항목
- [ ] 200 응답
- [ ] 나간 채팅방이 목록에서 제거
- [ ] 상대방은 여전히 채팅방에 존재

---

## WebSocket 테스트

### TC-CHAT-WS-001: WebSocket 연결

**연결 URL**: `ws://localhost:8080/ws/chat`

### 연결 방법 (wscat 예시)
```bash
wscat -c "ws://localhost:8080/ws/chat" \
  -H "Authorization: Bearer {ACCESS_TOKEN_A}"
```

### 확인 항목
- [ ] WebSocket 연결 성공
- [ ] 인증 없이 연결 시 거부

---

### TC-CHAT-WS-002: 채팅 구독 (Subscribe)

**구독 경로**: `/sub/chat/{chatId}`

### STOMP 구독 메시지
```json
SUBSCRIBE
destination:/sub/chat/{CHAT_ID}
```

### 확인 항목
- [ ] 구독 성공
- [ ] 해당 채팅방 참여자가 아닌 사용자 구독 시 거부

---

### TC-CHAT-WS-003: 메시지 전송

**메시지 경로**: `/chat/{chatId}`

### STOMP 전송 메시지
```json
SEND
destination:/chat/{CHAT_ID}

{
  "message": "안녕하세요!"
}
```

### 확인 항목
- [ ] User B의 구독 채널에 메시지 도착 확인
- [ ] REST API로 메시지 목록 조회 시 전송한 메시지 포함
- [ ] lastMessage, lastMessageAt 업데이트 확인

---

### TC-CHAT-WS-004: 읽음 처리 (WebSocket)

**경로**: `/chat/{chatId}/read`

### 확인 항목
- [ ] 읽음 처리 후 unreadCount 0 확인
- [ ] 상대방에게 읽음 이벤트 전달 여부

---

## 체크리스트 요약

| TC | 설명 | 결과 |
|----|------|------|
| TC-CHAT-001 | 채팅방 생성 | ⬜ |
| TC-CHAT-002 | 내 채팅방 목록 | ⬜ |
| TC-CHAT-003 | 메시지 목록 | ⬜ |
| TC-CHAT-004 | 읽음 처리 (REST) | ⬜ |
| TC-CHAT-005 | 채팅방 나가기 | ⬜ |
| TC-CHAT-WS-001 | WebSocket 연결 | ⬜ |
| TC-CHAT-WS-002 | 채팅 구독 | ⬜ |
| TC-CHAT-WS-003 | 메시지 전송 (실시간) | ⬜ |
| TC-CHAT-WS-004 | 읽음 처리 (WebSocket) | ⬜ |
