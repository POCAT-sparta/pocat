package com.rocketcrew.pocat.domain.ai.assistant.service;

import com.rocketcrew.pocat.domain.ai.assistant.entity.AiChatSession;
import com.rocketcrew.pocat.domain.ai.assistant.entity.AiChatMessage;
import com.rocketcrew.pocat.domain.ai.assistant.repository.AiChatSessionRepository;
import com.rocketcrew.pocat.domain.ai.assistant.repository.AiChatMessageRepository;
import com.rocketcrew.pocat.global.exception.common.ServiceException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * AI 채팅 세션 관리 서비스.
 * 세션 생성/조회, 메시지 저장, 토큰 추적.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class AiChatSessionService {

    private final AiChatSessionRepository sessionRepository;
    private final AiChatMessageRepository messageRepository;

    /**
     * 세션 조회 또는 생성.
     *
     * @param userId 사용자 ID
     * @param sessionUuid 세션 UUID
     * @return 세션 DB ID
     */
    public Long getOrCreateSession(Long userId, String sessionUuid) {
        return sessionRepository.findByUserIdAndSessionUuid(userId, sessionUuid)
                .map(AiChatSession::getId)
                .orElseGet(() -> {
                    AiChatSession newSession = AiChatSession.builder()
                            .userId(userId)
                            .sessionUuid(sessionUuid)
                            .totalTokens(0)
                            .isExpired(false)
                            .lastActiveAt(LocalDateTime.now())
                            .build();
                    AiChatSession saved = sessionRepository.save(newSession);
                    log.info("Created new session: sessionId={}, userId={}", saved.getId(), userId);
                    return saved.getId();
                });
    }

    /**
     * 세션 소유자 검증.
     *
     * @param sessionUuid 세션 UUID
     * @param userId 사용자 ID
     */
    public void validateSessionOwner(String sessionUuid, Long userId) {
        boolean isOwner = sessionRepository.findBySessionUuid(sessionUuid)
                .map(session -> session.getUserId().equals(userId))
                .orElse(false);

        if (!isOwner) {
            log.warn("Session ownership validation failed: sessionUuid={}, userId={}", sessionUuid, userId);
            throw new ServiceException("세션에 대한 접근 권한이 없습니다");
        }
    }

    /**
     * 최근 N턴 메시지 로드.
     *
     * @param sessionId 세션 DB ID
     * @param limit 조회할 메시지 개수
     * @return 메시지 목록 (역순)
     */
    public List<String> getRecentMessages(Long sessionId, int limit) {
        return messageRepository.findByAiChatSessionIdOrderByCreatedAtDesc(sessionId).stream()
                .limit(limit)
                .map(msg -> msg.getRole() + ": " + msg.getContent())
                .collect(Collectors.toList());
    }

    /**
     * 메시지 저장.
     *
     * @param sessionId 세션 DB ID
     * @param role 역할 (user, assistant, system)
     * @param content 메시지 내용
     * @param tokenCount 토큰 개수
     */
    public void addMessage(Long sessionId, String role, String content, int tokenCount) {
        AiChatMessage message = AiChatMessage.builder()
                .aiChatSessionId(sessionId)
                .role(role)
                .content(content)
                .tokenCount(tokenCount)
                .build();
        messageRepository.save(message);
        log.debug("Message saved: sessionId={}, role={}, tokens={}", sessionId, role, tokenCount);
    }

    /**
     * 세션 누적 토큰 업데이트.
     *
     * @param sessionId 세션 DB ID
     * @param tokens 추가 토큰 수
     */
    public void updateSessionTokens(Long sessionId, int tokens) {
        sessionRepository.findById(sessionId).ifPresent(session -> {
            session.addTokens(tokens);
            session.updateLastActiveAt(LocalDateTime.now());
            sessionRepository.save(session);
            log.debug("Session tokens updated: sessionId={}, totalTokens={}", sessionId, session.getTotalTokens());
        });
    }
}
