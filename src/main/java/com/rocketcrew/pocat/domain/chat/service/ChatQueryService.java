package com.rocketcrew.pocat.domain.chat.service;

import com.rocketcrew.pocat.domain.chat.dto.response.ChatMessageResponse;
import com.rocketcrew.pocat.domain.chat.dto.response.ChatRoomListResponse;
import com.rocketcrew.pocat.domain.chat.entity.Chat;
import com.rocketcrew.pocat.domain.chat.entity.ChatMessage;
import com.rocketcrew.pocat.domain.chat.repository.ChatMessageRepository;
import com.rocketcrew.pocat.domain.chat.repository.ChatRepository;
import com.rocketcrew.pocat.domain.community.tradepost.entity.TradePost;
import com.rocketcrew.pocat.domain.community.tradepost.repository.TradePostRepository;
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
import java.util.stream.Stream;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ChatQueryService {

    private final ChatRepository chatRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final TradePostRepository tradePostRepository;
    private final UserRepository userRepository;

    public List<ChatRoomListResponse> getMyChats(Long userId) {
        List<Chat> chats = chatRepository.findMyChats(userId);

        Set<Long> postIds = chats.stream().map(Chat::getPostId).collect(Collectors.toSet());
        Set<Long> userIds = chats.stream()
                .flatMap(c -> Stream.of(c.getOwnerId(), c.getGuestId()))
                .collect(Collectors.toSet());

        Map<Long, String> postTitles = tradePostRepository.findAllById(postIds).stream()
                .collect(Collectors.toMap(TradePost::getId, TradePost::getTitle));

        Map<Long, String> nicknames = userRepository.findAllById(userIds).stream()
                .collect(Collectors.toMap(User::getId, User::getNickname));

        return chats.stream().map(chat -> {
            Long opponentId = chat.getOwnerId().equals(userId) ? chat.getGuestId() : chat.getOwnerId();
            String lastMessage = chatMessageRepository
                    .findLastMessage(chat.getId())
                    .map(ChatMessage::getMessage)
                    .orElse(null);
            return new ChatRoomListResponse(
                    chat.getId(),
                    postTitles.getOrDefault(chat.getPostId(), ""),
                    nicknames.getOrDefault(opponentId, ""),
                    lastMessage,
                    chat.getStatus(),
                    chat.getUpdatedAt()
            );
        }).toList();
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
