package com.rocketcrew.pocat.domain.ai.session.repository;

import com.rocketcrew.pocat.domain.ai.session.entity.AiChatMessage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AiChatMessageRepository extends JpaRepository<AiChatMessage, Long> {

    List<AiChatMessage> findTop10BySessionIdOrderByCreatedAtDesc(Long sessionId);

    List<AiChatMessage> findBySessionIdOrderByCreatedAtAsc(Long sessionId);
}
