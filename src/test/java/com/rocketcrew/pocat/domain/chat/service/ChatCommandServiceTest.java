package com.rocketcrew.pocat.domain.chat.service;

import com.rocketcrew.pocat.domain.chat.dto.request.CreateChatRequest;
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
import com.rocketcrew.pocat.domain.user.enums.UserRole;
import com.rocketcrew.pocat.domain.user.repository.UserRepository;
import com.rocketcrew.pocat.global.exception.common.ErrorCode;
import com.rocketcrew.pocat.global.exception.common.ServiceException;
import com.rocketcrew.pocat.global.exception.domain.ChatException;
import com.rocketcrew.pocat.global.filter.BadWordFilterService;
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
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willDoNothing;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("ChatCommandService")
class ChatCommandServiceTest {

    @InjectMocks
    private ChatCommandService chatCommandService;

    @Mock
    private ChatRepository chatRepository;

    @Mock
    private ChatMessageRepository chatMessageRepository;

    @Mock
    private TradePostRepository tradePostRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private BadWordFilterService badWordFilterService;

    private TradePost tradePost;
    private User owner;
    private User guest;
    private Chat chat;

    @BeforeEach
    void setUp() {
        owner = User.builder()
                .email("owner@test.com")
                .password("pw")
                .nickname("글쓴이")
                .userRole(UserRole.USER)
                .build();
        ReflectionTestUtils.setField(owner, "id", 1L);

        guest = User.builder()
                .email("guest@test.com")
                .password("pw")
                .nickname("방문자")
                .userRole(UserRole.USER)
                .build();
        ReflectionTestUtils.setField(guest, "id", 2L);

        tradePost = TradePost.builder()
                .userId(1L)
                .title("거래 게시글")
                .content("내용")
                .price(10000L)
                .thumbnail("thumb.jpg")
                .viewCount(0)
                .build();
        ReflectionTestUtils.setField(tradePost, "id", 5L);

        chat = Chat.builder()
                .ownerId(1L)
                .guestId(2L)
                .postId(5L)
                .status(ChatStatus.ACTIVE)
                .ownerLeft(false)
                .guestLeft(false)
                .build();
        ReflectionTestUtils.setField(chat, "id", 100L);
        willDoNothing().given(badWordFilterService).validate(any());
    }

    @Nested
    @DisplayName("createChat()")
    class CreateChat {

        @Test
        @DisplayName("성공: 채팅방 생성")
        void success() {
            CreateChatRequest request = new CreateChatRequest(5L);
            given(tradePostRepository.findById(5L)).willReturn(Optional.of(tradePost));
            given(chatRepository.existsByPostIdAndGuestId(5L, 2L)).willReturn(false);
            given(chatRepository.save(any(Chat.class))).willReturn(chat);

            ChatResponse response = chatCommandService.createChat(2L, request);

            assertThat(response).isNotNull();
            assertThat(response.postId()).isEqualTo(5L);
            assertThat(response.ownerId()).isEqualTo(1L);
            assertThat(response.guestId()).isEqualTo(2L);
        }

        @Test
        @DisplayName("실패: 거래 게시글 없음 - TRADE_POST_NOT_FOUND")
        void tradePostNotFound() {
            CreateChatRequest request = new CreateChatRequest(999L);
            given(tradePostRepository.findById(999L)).willReturn(Optional.empty());

            assertThatThrownBy(() -> chatCommandService.createChat(2L, request))
                    .isInstanceOf(ChatException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.TRADE_POST_NOT_FOUND);
        }

        @Test
        @DisplayName("실패: 자신의 게시글에 채팅 - CHAT_SELF_CHAT")
        void selfChat() {
            CreateChatRequest request = new CreateChatRequest(5L);
            given(tradePostRepository.findById(5L)).willReturn(Optional.of(tradePost));

            // guestId == post.getUserId() == 1L
            assertThatThrownBy(() -> chatCommandService.createChat(1L, request))
                    .isInstanceOf(ChatException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.CHAT_SELF_CHAT);
        }

        @Test
        @DisplayName("실패: 이미 채팅방 존재 - CHAT_ALREADY_EXISTS (existsByPostIdAndGuestId)")
        void alreadyExists_fromCheck() {
            CreateChatRequest request = new CreateChatRequest(5L);
            given(tradePostRepository.findById(5L)).willReturn(Optional.of(tradePost));
            given(chatRepository.existsByPostIdAndGuestId(5L, 2L)).willReturn(true);

            assertThatThrownBy(() -> chatCommandService.createChat(2L, request))
                    .isInstanceOf(ChatException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.CHAT_ALREADY_EXISTS);
        }

        @Test
        @DisplayName("실패: DB 중복 - CHAT_ALREADY_EXISTS (DataIntegrityViolationException)")
        void alreadyExists_fromDb() {
            CreateChatRequest request = new CreateChatRequest(5L);
            given(tradePostRepository.findById(5L)).willReturn(Optional.of(tradePost));
            given(chatRepository.existsByPostIdAndGuestId(5L, 2L)).willReturn(false);
            given(chatRepository.save(any(Chat.class)))
                    .willThrow(new DataIntegrityViolationException("unique constraint"));

            assertThatThrownBy(() -> chatCommandService.createChat(2L, request))
                    .isInstanceOf(ChatException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.CHAT_ALREADY_EXISTS);
        }
    }

    @Nested
    @DisplayName("sendMessage()")
    class SendMessage {

        @Test
        @DisplayName("성공: 메시지 전송 및 DTO 반환")
        void success() {
            given(chatRepository.findByIdAndParticipant(100L, 2L)).willReturn(Optional.of(chat));
            given(userRepository.findById(2L)).willReturn(Optional.of(guest));

            ChatMessage savedMessage = ChatMessage.builder()
                    .chatId(100L)
                    .senderId(2L)
                    .message("안녕하세요")
                    .isRead(false)
                    .build();
            ReflectionTestUtils.setField(savedMessage, "id", 200L);

            given(chatMessageRepository.save(any(ChatMessage.class))).willReturn(savedMessage);

            ChatMessagePublishDto dto = chatCommandService.sendMessage(100L, 2L, "안녕하세요");

            assertThat(dto).isNotNull();
            assertThat(dto.message()).isEqualTo("안녕하세요");
            assertThat(dto.senderId()).isEqualTo(2L);
            assertThat(dto.senderNickname()).isEqualTo("방문자");
            verify(chatMessageRepository).save(any(ChatMessage.class));
        }

        @Test
        @DisplayName("실패: 채팅방 참여자가 아님 - CHAT_FORBIDDEN")
        void chatForbidden() {
            given(chatRepository.findByIdAndParticipant(100L, 99L)).willReturn(Optional.empty());

            assertThatThrownBy(() -> chatCommandService.sendMessage(100L, 99L, "메시지"))
                    .isInstanceOf(ChatException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.CHAT_FORBIDDEN);
        }

        @Test
        @DisplayName("실패: 금지어 포함 메시지 - CONTAINS_BAD_WORD")
        void containsBadWord_throwsException() {
            given(chatRepository.findByIdAndParticipant(100L, 2L)).willReturn(Optional.of(chat));
            given(userRepository.findById(2L)).willReturn(Optional.of(guest));
            willThrow(new ServiceException(ErrorCode.CONTAINS_BAD_WORD))
                    .given(badWordFilterService).validate(any());

            assertThatThrownBy(() -> chatCommandService.sendMessage(100L, 2L, "욕설이포함된메시지"))
                    .isInstanceOf(ServiceException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.CONTAINS_BAD_WORD);
        }
    }

    @Nested
    @DisplayName("markAsRead()")
    class MarkAsRead {

        @Test
        @DisplayName("성공: 읽음 처리")
        void success() {
            given(chatRepository.findByIdAndParticipant(100L, 2L)).willReturn(Optional.of(chat));
            willDoNothing().given(chatMessageRepository).markAllAsRead(100L, 2L);

            chatCommandService.markAsRead(100L, 2L);

            verify(chatMessageRepository).markAllAsRead(100L, 2L);
        }
    }

    @Nested
    @DisplayName("leaveChat()")
    class LeaveChat {

        @Test
        @DisplayName("성공: owner 퇴장 - ownerLeft=true, 양쪽 미완료이므로 삭제 안 됨")
        void ownerLeaves() {
            given(chatRepository.findByIdAndParticipant(100L, 1L)).willReturn(Optional.of(chat));

            chatCommandService.leaveChat(100L, 1L);

            assertThat(chat.isOwnerLeft()).isTrue();
            assertThat(chat.isGuestLeft()).isFalse();
            verify(chatRepository, never()).delete(chat);
        }

        @Test
        @DisplayName("성공: guest 퇴장 - guestLeft=true, 양쪽 미완료이므로 삭제 안 됨")
        void guestLeaves() {
            given(chatRepository.findByIdAndParticipant(100L, 2L)).willReturn(Optional.of(chat));

            chatCommandService.leaveChat(100L, 2L);

            assertThat(chat.isGuestLeft()).isTrue();
            assertThat(chat.isOwnerLeft()).isFalse();
            verify(chatRepository, never()).delete(chat);
        }

        @Test
        @DisplayName("실패 - 채팅 참여자가 아닌 사용자 퇴장 시도 → CHAT_FORBIDDEN")
        void leaveChat_notParticipant_throwsChatForbidden() {
            // given: user 99L is neither owner(1L) nor guest(2L)
            Long chatId = 1L;
            Long nonParticipantId = 99L;
            given(chatRepository.findByIdAndParticipant(chatId, nonParticipantId))
                    .willReturn(Optional.empty());

            // when / then
            assertThatThrownBy(() -> chatCommandService.leaveChat(chatId, nonParticipantId))
                    .isInstanceOf(ChatException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.CHAT_FORBIDDEN);
        }

        @Test
        @DisplayName("성공: 양쪽 모두 퇴장 → 채팅방 삭제")
        void bothLeave_chatDeleted() {
            // owner already left
            Chat chatWithOwnerLeft = Chat.builder()
                    .ownerId(1L)
                    .guestId(2L)
                    .postId(5L)
                    .status(ChatStatus.ACTIVE)
                    .ownerLeft(true)
                    .guestLeft(false)
                    .build();
            ReflectionTestUtils.setField(chatWithOwnerLeft, "id", 100L);

            given(chatRepository.findByIdAndParticipant(100L, 2L)).willReturn(Optional.of(chatWithOwnerLeft));

            chatCommandService.leaveChat(100L, 2L);

            assertThat(chatWithOwnerLeft.isBothLeft()).isTrue();
            verify(chatRepository).delete(chatWithOwnerLeft);
        }
    }
}
