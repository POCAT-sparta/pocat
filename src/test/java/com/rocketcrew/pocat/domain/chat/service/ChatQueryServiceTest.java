package com.rocketcrew.pocat.domain.chat.service;

import com.rocketcrew.pocat.domain.chat.dto.response.ChatMessageResponse;
import com.rocketcrew.pocat.domain.chat.dto.response.ChatRoomListResponse;
import com.rocketcrew.pocat.domain.chat.entity.Chat;
import com.rocketcrew.pocat.domain.chat.entity.ChatMessage;
import com.rocketcrew.pocat.domain.chat.entity.enums.ChatStatus;
import com.rocketcrew.pocat.domain.chat.repository.ChatMessageRepository;
import com.rocketcrew.pocat.domain.chat.repository.ChatRepository;
import com.rocketcrew.pocat.domain.user.entity.User;
import com.rocketcrew.pocat.domain.user.enums.UserRole;
import com.rocketcrew.pocat.domain.user.repository.UserRepository;
import com.rocketcrew.pocat.global.exception.common.ErrorCode;
import com.rocketcrew.pocat.global.exception.domain.ChatException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("ChatQueryService")
class ChatQueryServiceTest {

    @InjectMocks
    private ChatQueryService chatQueryService;

    @Mock
    private ChatRepository chatRepository;

    @Mock
    private ChatMessageRepository chatMessageRepository;

    @Mock
    private UserRepository userRepository;

    private Chat chat;
    private User sender;

    @BeforeEach
    void setUp() {
        chat = Chat.builder()
                .ownerId(1L)
                .guestId(2L)
                .postId(5L)
                .status(ChatStatus.ACTIVE)
                .ownerLeft(false)
                .guestLeft(false)
                .build();
        ReflectionTestUtils.setField(chat, "id", 100L);

        sender = User.builder()
                .email("sender@test.com")
                .password("pw")
                .nickname("발신자")
                .userRole(UserRole.USER)
                .build();
        ReflectionTestUtils.setField(sender, "id", 2L);
    }

    @Nested
    @DisplayName("getMyChats()")
    class GetMyChats {

        @Test
        @DisplayName("성공: 내 채팅 목록 반환")
        void success() {
            ChatRoomListResponse roomResponse = new ChatRoomListResponse(
                    100L, "거래 게시글", "방문자", "안녕하세요", ChatStatus.ACTIVE, LocalDateTime.now());
            given(chatRepository.findMyChatsWithDetails(1L)).willReturn(List.of(roomResponse));

            List<ChatRoomListResponse> result = chatQueryService.getMyChats(1L);

            assertThat(result).hasSize(1);
            assertThat(result.get(0).chatId()).isEqualTo(100L);
        }
    }

    @Nested
    @DisplayName("getMessages()")
    class GetMessages {

        @Test
        @DisplayName("성공: 메시지 조회 및 닉네임 매핑")
        void success() {
            Pageable pageable = PageRequest.of(0, 30);
            given(chatRepository.findByIdAndParticipant(100L, 2L)).willReturn(Optional.of(chat));

            ChatMessage msg = ChatMessage.builder()
                    .chatId(100L)
                    .senderId(2L)
                    .message("안녕하세요")
                    .isRead(false)
                    .build();
            ReflectionTestUtils.setField(msg, "id", 1L);

            given(chatMessageRepository.findByChatId(100L, pageable))
                    .willReturn(new PageImpl<>(List.of(msg)));
            given(userRepository.findAllById(java.util.Set.of(2L))).willReturn(List.of(sender));

            Page<ChatMessageResponse> result = chatQueryService.getMessages(100L, 2L, pageable);

            assertThat(result.getContent()).hasSize(1);
            assertThat(result.getContent().get(0).senderNickname()).isEqualTo("발신자");
            assertThat(result.getContent().get(0).message()).isEqualTo("안녕하세요");
        }

        @Test
        @DisplayName("실패: 채팅방 참여자가 아님 - CHAT_FORBIDDEN")
        void chatForbidden() {
            Pageable pageable = PageRequest.of(0, 30);
            given(chatRepository.findByIdAndParticipant(100L, 99L)).willReturn(Optional.empty());

            assertThatThrownBy(() -> chatQueryService.getMessages(100L, 99L, pageable))
                    .isInstanceOf(ChatException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.CHAT_FORBIDDEN);
        }
    }
}
