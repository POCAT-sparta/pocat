package com.rocketcrew.pocat.domain.chat.repository;

import com.rocketcrew.pocat.domain.chat.entity.ChatMessage;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {

    List<ChatMessage> findByChatId(Long chatId);

    Page<ChatMessage> findByChatId(Long chatId, Pageable pageable);
}
