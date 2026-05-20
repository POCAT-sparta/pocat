package com.rocketcrew.pocat.domain.chat.repository;

import com.rocketcrew.pocat.domain.chat.entity.Chat;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface ChatRepository extends JpaRepository<Chat, Long>, ChatRepositoryCustom {

    boolean existsByPostIdAndGuestId(Long postId, Long guestId);

    @Query("SELECT c FROM Chat c WHERE c.id = :chatId AND (c.ownerId = :userId OR c.guestId = :userId)")
    Optional<Chat> findByIdAndParticipant(@Param("chatId") Long chatId, @Param("userId") Long userId);
}
