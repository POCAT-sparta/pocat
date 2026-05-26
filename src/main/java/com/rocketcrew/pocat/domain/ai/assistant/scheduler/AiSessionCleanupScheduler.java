package com.rocketcrew.pocat.domain.ai.assistant.scheduler;

import com.rocketcrew.pocat.domain.ai.assistant.repository.AiChatSessionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * AI 채팅 세션 자동 정리 스케줄러.
 * 30분 이상 비활성 세션을 만료 처리.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AiSessionCleanupScheduler {

    private final AiChatSessionRepository sessionRepository;

    private static final int INACTIVITY_MINUTES = 30;

    /**
     * 5분마다 실행: 비활성 세션 만료 처리.
     */
    @Scheduled(fixedRate = 300000) // 5분 = 300,000ms
    @Transactional
    public void expireOldSessions() {
        try {
            LocalDateTime threshold = LocalDateTime.now().minusMinutes(INACTIVITY_MINUTES);
            int expiredCount = sessionRepository.expireSessionsBeforeTime(threshold);

            if (expiredCount > 0) {
                log.info("AI sessions cleaned up: {} sessions expired", expiredCount);
            }
        } catch (Exception e) {
            log.error("Error during AI session cleanup: {}", e.getMessage(), e);
        }
    }
}
