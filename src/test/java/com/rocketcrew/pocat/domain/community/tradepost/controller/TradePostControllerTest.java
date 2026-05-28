package com.rocketcrew.pocat.domain.community.tradepost.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rocketcrew.pocat.domain.community.tradepost.dto.request.CreateTradePostRequest;
import com.rocketcrew.pocat.domain.community.tradepost.dto.request.UpdateTradePostRequest;
import com.rocketcrew.pocat.domain.community.tradepost.dto.response.CreateTradePost;
import com.rocketcrew.pocat.domain.community.tradepost.dto.response.TradePostListResponse;
import com.rocketcrew.pocat.domain.community.tradepost.dto.response.TradePostResponse;
import com.rocketcrew.pocat.domain.community.tradepost.dto.response.UpdateTradePostResponse;
import com.rocketcrew.pocat.domain.community.tradepost.service.TradePostCommandService;
import com.rocketcrew.pocat.domain.community.tradepost.service.TradePostQueryService;
import com.rocketcrew.pocat.global.exception.common.ErrorCode;
import com.rocketcrew.pocat.global.exception.common.GlobalExceptionHandler;
import com.rocketcrew.pocat.global.exception.domain.TradePostException;
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

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willDoNothing;
import static org.mockito.BDDMockito.willThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("TradePostController")
class TradePostControllerTest {

    private MockMvc mockMvc;

    @InjectMocks
    private TradePostController tradePostController;

    @Mock
    private TradePostCommandService tradePostCommandService;

    @Mock
    private TradePostQueryService tradePostQueryService;

    private CustomUserDetails userDetails;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private TradePostListResponse sampleListResponse() {
        return new TradePostListResponse(10L, "거래 제목", "테스터", 10000L,
                "http://example.com/thumb.jpg", 0, LocalDateTime.now());
    }

    private TradePostResponse sampleDetailResponse() {
        return new TradePostResponse(10L, "거래 제목", "내용", 1L, "테스터",
                10000L, "http://example.com/thumb.jpg", 0,
                LocalDateTime.now(), LocalDateTime.now());
    }

    @BeforeEach
    void setUp() {
        userDetails = new TestCustomUserDetails(1L, "USER");
        mockMvc = MockMvcBuilders.standaloneSetup(tradePostController)
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
    @DisplayName("GET /api/v1/posts/trade")
    class GetPosts {

        @Test
        @DisplayName("200 성공: 거래 게시글 목록 조회")
        void success() throws Exception {
            given(tradePostQueryService.getPosts(any(), any(), any(), any(Pageable.class)))
                    .willReturn(new PageImpl<>(List.of(sampleListResponse())));

            mockMvc.perform(get("/api/v1/posts/trade"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true));
        }
    }

    @Nested
    @DisplayName("GET /api/v1/posts/trade/me")
    class GetMyPosts {

        @Test
        @DisplayName("200 성공: 내 거래 게시글 목록 조회")
        void success() throws Exception {
            given(tradePostQueryService.getPostsByUserId(eq(1L), any(Pageable.class)))
                    .willReturn(new PageImpl<>(List.of(sampleListResponse())));

            mockMvc.perform(get("/api/v1/posts/trade/me"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true));
        }
    }

    @Nested
    @DisplayName("GET /api/v1/posts/trade/{tradePostId}")
    class GetPost {

        @Test
        @DisplayName("200 성공: 단건 조회")
        void success() throws Exception {
            given(tradePostQueryService.getPost(eq(10L), any(), any()))
                    .willReturn(sampleDetailResponse());

            mockMvc.perform(get("/api/v1/posts/trade/10"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true));
        }

        @Test
        @DisplayName("404 실패: 거래 게시글 없음")
        void fail_notFound() throws Exception {
            given(tradePostQueryService.getPost(eq(999L), any(), any()))
                    .willThrow(new TradePostException(ErrorCode.TRADE_POST_NOT_FOUND));

            mockMvc.perform(get("/api/v1/posts/trade/999"))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("TRADE_POST_NOT_FOUND"));
        }
    }

    @Nested
    @DisplayName("POST /api/v1/posts/trade")
    class CreatePost {

        @Test
        @DisplayName("201 성공: 거래 게시글 생성")
        void success() throws Exception {
            CreateTradePostRequest request = new CreateTradePostRequest(
                    "거래 제목", "내용", 10000L, "http://example.com/thumb.jpg");
            given(tradePostCommandService.createPost(eq(1L), any(CreateTradePostRequest.class)))
                    .willReturn(new CreateTradePost(10L, "거래 제목"));

            mockMvc.perform(post("/api/v1/posts/trade")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.success").value(true));
        }
    }

    @Nested
    @DisplayName("PATCH /api/v1/posts/trade/{tradePostId}")
    class UpdatePost {

        @Test
        @DisplayName("200 성공: 거래 게시글 수정")
        void success() throws Exception {
            UpdateTradePostRequest request = new UpdateTradePostRequest(
                    "수정 제목", "수정 내용", 20000L, "http://example.com/new.jpg");
            given(tradePostCommandService.updatePost(eq(10L), eq(1L), any(UpdateTradePostRequest.class)))
                    .willReturn(new UpdateTradePostResponse(10L, "수정 제목", 20000L));

            mockMvc.perform(patch("/api/v1/posts/trade/10")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true));
        }

        @Test
        @DisplayName("403 실패: 권한 없음")
        void fail_forbidden() throws Exception {
            UpdateTradePostRequest request = new UpdateTradePostRequest(
                    "수정 제목", "수정 내용", 20000L, "http://example.com/new.jpg");
            given(tradePostCommandService.updatePost(eq(10L), eq(1L), any(UpdateTradePostRequest.class)))
                    .willThrow(new TradePostException(ErrorCode.USER_FORBIDDEN));

            mockMvc.perform(patch("/api/v1/posts/trade/10")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("USER_FORBIDDEN"));
        }

        @Test
        @DisplayName("404 실패: 거래 게시글 없음")
        void fail_notFound() throws Exception {
            UpdateTradePostRequest request = new UpdateTradePostRequest(
                    "수정 제목", "수정 내용", 20000L, "http://example.com/new.jpg");
            given(tradePostCommandService.updatePost(eq(999L), eq(1L), any(UpdateTradePostRequest.class)))
                    .willThrow(new TradePostException(ErrorCode.TRADE_POST_NOT_FOUND));

            mockMvc.perform(patch("/api/v1/posts/trade/999")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("TRADE_POST_NOT_FOUND"));
        }
    }

    @Nested
    @DisplayName("DELETE /api/v1/posts/trade/{tradePostId}")
    class DeletePost {

        @Test
        @DisplayName("200 성공: 거래 게시글 삭제")
        void success() throws Exception {
            willDoNothing().given(tradePostCommandService).deletePost(eq(10L), eq(1L), anyString());

            mockMvc.perform(delete("/api/v1/posts/trade/10"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true));
        }

        @Test
        @DisplayName("403 실패: 권한 없음")
        void fail_forbidden() throws Exception {
            willThrow(new TradePostException(ErrorCode.USER_FORBIDDEN))
                    .given(tradePostCommandService).deletePost(eq(10L), eq(1L), anyString());

            mockMvc.perform(delete("/api/v1/posts/trade/10"))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("USER_FORBIDDEN"));
        }
    }
}
