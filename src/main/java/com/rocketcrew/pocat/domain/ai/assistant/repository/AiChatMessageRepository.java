package com.rocketcrew.pocat.domain.ai.assistant.repository;

import com.rocketcrew.pocat.domain.ai.assistant.entity.AiChatMessage;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AiChatMessageRepository extends JpaRepository<AiChatMessage, Long> {

    /**
     * 세션의 메시지를 생성 시간 역순으로 조회.
     *
     * @param aiChatSessionId 세션 ID
     * @param pageable 페이지 정보
     * @return 메시지 목록 (최신순)
     */
    List<AiChatMessage> findByAiChatSessionIdOrderByCreatedAtDesc(Long aiChatSessionId, Pageable pageable);
}
