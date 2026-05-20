package com.rocketcrew.pocat.domain.chat.service;

import com.rocketcrew.pocat.domain.chat.dto.request.CreateChatRequest;
import com.rocketcrew.pocat.domain.chat.dto.response.ChatEventType;
import com.rocketcrew.pocat.domain.chat.dto.response.ChatMessagePublishDto;
import com.rocketcrew.pocat.domain.chat.dto.response.ChatResponse;
import com.rocketcrew.pocat.domain.chat.entity.Chat;
import com.rocketcrew.pocat.domain.chat.entity.ChatMessage;
import com.rocketcrew.pocat.domain.chat.entity.enums.ChatStatus;
import com.rocketcrew.pocat.domain.chat.repository.ChatMessageRepository;
import com.rocketcrew.pocat.domain.chat.repository.ChatRepository;
import com.rocketcrew.pocat.domain.community.tradepost.entity.TradePost;
import com.rocketcrew.pocat.domain.community.tradepost.repository.TradePostRepository;
import com.rocketcrew.pocat.domain.user.entity.User;
import com.rocketcrew.pocat.domain.user.repository.UserRepository;
import com.rocketcrew.pocat.global.exception.common.ErrorCode;
import com.rocketcrew.pocat.global.exception.domain.ChatException;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class ChatCommandService {

    private final ChatRepository chatRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final TradePostRepository tradePostRepository;
    private final UserRepository userRepository;

    public ChatResponse createChat(Long guestId, CreateChatRequest request) {
        TradePost post = tradePostRepository.findById(request.postId())
                .orElseThrow(() -> new ChatException(ErrorCode.TRADE_POST_NOT_FOUND));
        if (post.getUserId().equals(guestId)) {
            throw new ChatException(ErrorCode.CHAT_SELF_CHAT);
        }
        if (chatRepository.existsByPostIdAndGuestId(request.postId(), guestId)) {
            throw new ChatException(ErrorCode.CHAT_ALREADY_EXISTS);
        }
        Chat chat = Chat.builder()
                .ownerId(post.getUserId())
                .guestId(guestId)
                .postId(request.postId())
                .guestLeft(false)
                .ownerLeft(false)
                .status(ChatStatus.ACTIVE)
                .build();
        try {
            return ChatResponse.from(chatRepository.save(chat));
        } catch (DataIntegrityViolationException e) {
            throw new ChatException(ErrorCode.CHAT_ALREADY_EXISTS);
        }
    }

    public ChatMessagePublishDto sendMessage(Long chatId, Long senderId, String message) {
        chatRepository.findByIdAndParticipant(chatId, senderId)
                .orElseThrow(() -> new ChatException(ErrorCode.CHAT_FORBIDDEN));

        User sender = userRepository.findById(senderId)
                .orElseThrow(() -> new ChatException(ErrorCode.USER_NOT_FOUND));

        ChatMessage saved = chatMessageRepository.save(ChatMessage.builder()
                .chatId(chatId)
                .senderId(senderId)
                .message(message)
                .isRead(false)
                .build());

        return new ChatMessagePublishDto(
                ChatEventType.MESSAGE,
                chatId,
                senderId,
                sender.getNickname(),
                saved.getMessage(),
                saved.getCreatedAt()
        );
    }

    public void markAsRead(Long chatId, Long userId) {
        chatRepository.findByIdAndParticipant(chatId, userId)
                .orElseThrow(() -> new ChatException(ErrorCode.CHAT_FORBIDDEN));
        chatMessageRepository.markAllAsRead(chatId, userId);
    }

    public void leaveChat(Long chatId, Long userId) {
        Chat chat = chatRepository.findByIdAndParticipant(chatId, userId)
                .orElseThrow(() -> new ChatException(ErrorCode.CHAT_FORBIDDEN));

        if (chat.getOwnerId().equals(userId)) {
            chat.markOwnerLeft();
        } else {
            chat.markGuestLeft();
        }

        if (chat.isBothLeft()) {
            chatRepository.delete(chat);
        }
    }
}
