package com.rocketcrew.pocat.domain.chat.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rocketcrew.pocat.domain.chat.dto.request.CreateChatRequest;
import com.rocketcrew.pocat.domain.chat.dto.response.ChatMessageResponse;
import com.rocketcrew.pocat.domain.chat.dto.response.ChatResponse;
import com.rocketcrew.pocat.domain.chat.dto.response.ChatRoomListResponse;
import com.rocketcrew.pocat.domain.chat.entity.enums.ChatStatus;
import com.rocketcrew.pocat.domain.chat.service.ChatCommandService;
import com.rocketcrew.pocat.domain.chat.service.ChatQueryService;
import com.rocketcrew.pocat.global.exception.common.ErrorCode;
import com.rocketcrew.pocat.global.exception.common.GlobalExceptionHandler;
import com.rocketcrew.pocat.global.exception.domain.ChatException;
import com.rocketcrew.pocat.global.ratelimit.RedisRateLimiter;
import com.rocketcrew.pocat.global.ratelimit.RateLimitProperties;
import com.rocketcrew.pocat.global.security.CustomUserDetails;
import com.rocketcrew.pocat.support.TestCustomUserDetails;
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
import org.springframework.core.MethodParameter;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableHandlerMethodArgumentResolver;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willDoNothing;
import static org.mockito.BDDMockito.willThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("ChatController")
class ChatControllerTest {

    private MockMvc mockMvc;

    @InjectMocks
    private ChatController chatController;

    @Mock
    private ChatCommandService chatCommandService;

    @Mock
    private ChatQueryService chatQueryService;

    @Mock
    private RedisRateLimiter redisRateLimiter;

    @Mock
    private RateLimitProperties rateLimitProperties;

    private CustomUserDetails userDetails;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private ChatResponse sampleChatResponse() {
        return new ChatResponse(100L, 5L, 1L, 2L, ChatStatus.ACTIVE);
    }

    private ChatRoomListResponse sampleRoomResponse() {
        return new ChatRoomListResponse(100L, "거래 게시글", "방문자", "안녕하세요", ChatStatus.ACTIVE, LocalDateTime.now());
    }

    private ChatMessageResponse sampleMessageResponse() {
        return new ChatMessageResponse(1L, 2L, "방문자", "안녕하세요", false, LocalDateTime.now());
    }

    @BeforeEach
    void setUp() {
        userDetails = new TestCustomUserDetails(1L, "USER");
        given(redisRateLimiter.isAllowed(anyString(), anyInt(), anyLong())).willReturn(true);
        mockMvc = MockMvcBuilders.standaloneSetup(chatController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .setCustomArgumentResolvers(
                        new PageableHandlerMethodArgumentResolver(),
                        new HandlerMethodArgumentResolver() {
                            @Override
                            public boolean supportsParameter(MethodParameter parameter) {
                                return parameter.hasParameterAnnotation(AuthenticationPrincipal.class);
                            }

                            @Override
                            public Object resolveArgument(MethodParameter parameter,
                                                          ModelAndViewContainer mavContainer,
                                                          NativeWebRequest webRequest,
                                                          WebDataBinderFactory binderFactory) {
                                return userDetails;
                            }
                        })
                .build();
    }

    @Nested
    @DisplayName("POST /api/v1/chats")
    class CreateChat {

        @Test
        @DisplayName("201 성공: 채팅방 생성")
        void success() throws Exception {
            CreateChatRequest request = new CreateChatRequest(5L);
            given(chatCommandService.createChat(eq(1L), any(CreateChatRequest.class)))
                    .willReturn(sampleChatResponse());

            mockMvc.perform(post("/api/v1/chats")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.chatId").value(100L))
                    .andExpect(jsonPath("$.data.postId").value(5L));
        }

        @Test
        @DisplayName("400 실패: 자신의 게시글에 채팅 - CHAT_SELF_CHAT")
        void selfChat() throws Exception {
            CreateChatRequest request = new CreateChatRequest(5L);
            given(chatCommandService.createChat(eq(1L), any(CreateChatRequest.class)))
                    .willThrow(new ChatException(ErrorCode.CHAT_SELF_CHAT));

            mockMvc.perform(post("/api/v1/chats")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("CHAT_SELF_CHAT"));
        }

        @Test
        @DisplayName("409 실패: 이미 채팅방 존재 - CHAT_ALREADY_EXISTS")
        void alreadyExists() throws Exception {
            CreateChatRequest request = new CreateChatRequest(5L);
            given(chatCommandService.createChat(eq(1L), any(CreateChatRequest.class)))
                    .willThrow(new ChatException(ErrorCode.CHAT_ALREADY_EXISTS));

            mockMvc.perform(post("/api/v1/chats")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("CHAT_ALREADY_EXISTS"));
        }
    }

    @Nested
    @DisplayName("GET /api/v1/chats/me")
    class GetMyChats {

        @Test
        @DisplayName("200 성공: 내 채팅 목록 조회")
        void success() throws Exception {
            given(chatQueryService.getMyChats(1L)).willReturn(List.of(sampleRoomResponse()));

            mockMvc.perform(get("/api/v1/chats/me"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data[0].chatId").value(100L))
                    .andExpect(jsonPath("$.data[0].postTitle").value("거래 게시글"));
        }
    }

    @Nested
    @DisplayName("GET /api/v1/chats/{chatId}/messages")
    class GetMessages {

        @Test
        @DisplayName("200 성공: 메시지 목록 조회")
        void success() throws Exception {
            given(chatQueryService.getMessages(eq(100L), eq(1L), any(Pageable.class)))
                    .willReturn(new PageImpl<>(List.of(sampleMessageResponse())));

            mockMvc.perform(get("/api/v1/chats/100/messages"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.content[0].senderId").value(2L))
                    .andExpect(jsonPath("$.data.content[0].message").value("안녕하세요"));
        }

        @Test
        @DisplayName("403 실패: 채팅방 권한 없음 - CHAT_FORBIDDEN")
        void chatForbidden() throws Exception {
            given(chatQueryService.getMessages(eq(100L), eq(1L), any(Pageable.class)))
                    .willThrow(new ChatException(ErrorCode.CHAT_FORBIDDEN));

            mockMvc.perform(get("/api/v1/chats/100/messages"))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("CHAT_FORBIDDEN"));
        }
    }

    @Nested
    @DisplayName("PATCH /api/v1/chats/{chatId}/read")
    class MarkAsRead {

        @Test
        @DisplayName("200 성공: 읽음 처리")
        void success() throws Exception {
            willDoNothing().given(chatCommandService).markAsRead(100L, 1L);

            mockMvc.perform(patch("/api/v1/chats/100/read"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true));
        }
    }

    @Nested
    @DisplayName("DELETE /api/v1/chats/{chatId}")
    class LeaveChat {

        @Test
        @DisplayName("200 성공: 채팅방 나가기")
        void success() throws Exception {
            willDoNothing().given(chatCommandService).leaveChat(100L, 1L);

            mockMvc.perform(delete("/api/v1/chats/100"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true));
        }
    }
}
