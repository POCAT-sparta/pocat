package com.rocketcrew.pocat.domain.chat.repository;

import com.rocketcrew.pocat.domain.chat.entity.ChatMessage;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {

    Page<ChatMessage> findByChatId(Long chatId, Pageable pageable);

    @Query("SELECT m FROM ChatMessage m WHERE m.chatId = :chatId ORDER BY m.createdAt DESC LIMIT 1")
    Optional<ChatMessage> findLastMessage(@Param("chatId") Long chatId);

    @Modifying
    @Query("UPDATE ChatMessage m SET m.isRead = true WHERE m.chatId = :chatId AND m.senderId != :userId AND m.isRead = false")
    void markAllAsRead(@Param("chatId") Long chatId, @Param("userId") Long userId);
}
