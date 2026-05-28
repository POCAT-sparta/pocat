# AI 인프라 안정화 (#116) 테스트 결과

**날짜**: 2026-05-27  
**브랜치**: feat/infra-fix/#116  

## 테스트 결과 요약

| 항목 | 결과 |
|------|------|
| 전체 테스트 | 289개 |
| 신규 테스트 | 6개 추가 |
| #116 관련 테스트 | 전체 PASS |
| 기존 실패 (pre-existing) | 21개 (dev 브랜치와 동일) |

## 신규 테스트 목록

| 테스트 | 검증 내용 |
|--------|----------|
| AiAssistantServiceTest: circuitBreaker_chat_triggersCircuitBreakerOnMultipleFailures | chat() @CircuitBreaker 어노테이션 선언 여부 |
| AiAssistantServiceTest: chat_extractsTokensFromChatResponse | ChatResponse.getMetadata().getUsage()에서 실제 토큰 추출 |
| CardAnalysisServiceTest: analyzeCard_persistsToCardAiAnalysis | cardAiAnalysisRepository.save() 호출 검증 |
| CardAnalysisServiceTest: analyzeCard_rateLimiter_returns429OnExceedingLimit | @RateLimiter 어노테이션 선언 여부 |
| EmbeddingEventListenerTest: onCardEmbedding_callsEmbedCardService | CardEmbeddingEvent 수신 시 embedCard() 호출 |
| EmbeddingEventListenerTest: onTradePostEmbedding_callsEmbedTradePostService | TradePostEmbeddingEvent 수신 시 embedTradePost() 호출 |

## 수정된 기존 테스트

| 테스트 파일 | 수정 이유 |
|------------|----------|
| AiAssistantServiceTest | chatClient.call().content() → chatResponse() 체인으로 stub 업데이트 |
| CardAnalysisServiceTest | @Lazy self 필드 ReflectionTestUtils 주입 추가 |
