package com.rocketcrew.pocat.domain.chat.service;

import com.rocketcrew.pocat.domain.chat.dto.response.ChatMessageResponse;
import com.rocketcrew.pocat.domain.chat.dto.response.ChatRoomListResponse;
import com.rocketcrew.pocat.domain.chat.entity.ChatMessage;
import com.rocketcrew.pocat.domain.chat.repository.ChatMessageRepository;
import com.rocketcrew.pocat.domain.chat.repository.ChatRepository;
import com.rocketcrew.pocat.domain.user.entity.User;
import com.rocketcrew.pocat.domain.user.repository.UserRepository;
import com.rocketcrew.pocat.global.exception.common.ErrorCode;
import com.rocketcrew.pocat.global.exception.domain.ChatException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ChatQueryService {

    private final ChatRepository chatRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final UserRepository userRepository;

    public List<ChatRoomListResponse> getMyChats(Long userId) {
        return chatRepository.findMyChatsWithDetails(userId);
    }

    public Page<ChatMessageResponse> getMessages(Long chatId, Long userId, Pageable pageable) {
        chatRepository.findByIdAndParticipant(chatId, userId)
                .orElseThrow(() -> new ChatException(ErrorCode.CHAT_FORBIDDEN));

        Page<ChatMessage> messages = chatMessageRepository.findByChatId(chatId, pageable);
        Set<Long> senderIds = messages.stream().map(ChatMessage::getSenderId).collect(Collectors.toSet());
        Map<Long, String> nicknames = userRepository.findAllById(senderIds).stream()
                .collect(Collectors.toMap(User::getId, User::getNickname));

        return messages.map(msg -> ChatMessageResponse.from(msg, nicknames.getOrDefault(msg.getSenderId(), "")));
    }
}
