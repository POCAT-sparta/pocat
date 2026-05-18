package com.rocketcrew.pocat.domain.chat.service;

import com.rocketcrew.pocat.domain.chat.dto.request.CreateChatRequest;
import com.rocketcrew.pocat.domain.chat.dto.request.SendMessageRequest;
import com.rocketcrew.pocat.domain.chat.dto.response.ChatMessageResponse;
import com.rocketcrew.pocat.domain.chat.dto.response.ChatResponse;
import com.rocketcrew.pocat.domain.chat.entity.Chat;
import com.rocketcrew.pocat.domain.chat.entity.ChatMessage;
import com.rocketcrew.pocat.domain.chat.entity.ChatStatus;
import com.rocketcrew.pocat.domain.chat.repository.ChatMessageRepository;
import com.rocketcrew.pocat.domain.chat.repository.ChatRepository;
import com.rocketcrew.pocat.global.exception.common.ErrorCode;
import com.rocketcrew.pocat.global.exception.domain.ChatException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class ChatService {

    private final ChatRepository chatRepository;
    private final ChatMessageRepository chatMessageRepository;

    public ChatResponse createChat(Long ownerId, CreateChatRequest request) {
        Chat chat = Chat.builder()
                .ownerId(ownerId)
                .guestId(request.guestId())
                .postId(request.postId())
                .status(ChatStatus.ACTIVE)
                .build();
        return ChatResponse.from(chatRepository.save(chat));
    }

    @Transactional(readOnly = true)
    public Page<ChatResponse> getMyChats(Long userId, Pageable pageable) {
        return chatRepository.findByOwnerIdOrGuestId(userId, userId, pageable)
                .map(ChatResponse::from);
    }

    @Transactional(readOnly = true)
    public ChatResponse getChat(Long id) {
        Chat chat = chatRepository.findById(id)
                .orElseThrow(() -> new ChatException(ErrorCode.CHAT_NOT_FOUND));
        return ChatResponse.from(chat);
    }

    @Transactional(readOnly = true)
    public Page<ChatMessageResponse> getMessages(Long chatId, Pageable pageable) {
        return chatMessageRepository.findByChatId(chatId, pageable)
                .map(ChatMessageResponse::from);
    }

    public ChatMessageResponse sendMessage(Long chatId, Long senderId, SendMessageRequest request) {
        chatRepository.findById(chatId)
                .orElseThrow(() -> new ChatException(ErrorCode.CHAT_NOT_FOUND));
        ChatMessage chatMessage = ChatMessage.builder()
                .chatId(chatId)
                .senderId(senderId)
                .message(request.message())
                .isRead(false)
                .build();
        return ChatMessageResponse.from(chatMessageRepository.save(chatMessage));
    }
}
