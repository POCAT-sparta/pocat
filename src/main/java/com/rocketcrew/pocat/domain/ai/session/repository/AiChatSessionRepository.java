package com.rocketcrew.pocat.domain.ai.session.repository;

import com.rocketcrew.pocat.domain.ai.session.entity.AiChatSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface AiChatSessionRepository extends JpaRepository<AiChatSession, Long> {

    Optional<AiChatSession> findBySessionUuidAndIsExpiredFalse(String sessionUuid);

    List<AiChatSession> findByLastActiveAtBeforeAndIsExpiredFalse(LocalDateTime threshold);
}
