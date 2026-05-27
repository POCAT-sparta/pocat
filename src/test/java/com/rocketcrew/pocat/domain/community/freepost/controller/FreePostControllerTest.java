package com.rocketcrew.pocat.domain.community.freepost.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rocketcrew.pocat.domain.community.freepost.dto.request.CreateFreePostRequest;
import com.rocketcrew.pocat.domain.community.freepost.dto.request.UpdateFreePostRequest;
import com.rocketcrew.pocat.domain.community.freepost.dto.response.FreePostResponse;
import com.rocketcrew.pocat.domain.community.freepost.service.FreePostCommandService;
import com.rocketcrew.pocat.domain.community.freepost.service.FreePostQueryService;
import com.rocketcrew.pocat.domain.community.freepost.service.FreePostRankingService;
import com.rocketcrew.pocat.global.exception.common.ErrorCode;
import com.rocketcrew.pocat.global.exception.common.GlobalExceptionHandler;
import com.rocketcrew.pocat.global.exception.domain.FreePostException;
import com.rocketcrew.pocat.global.exception.domain.UserException;
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
@DisplayName("FreePostController")
class FreePostControllerTest {

    private MockMvc mockMvc;

    @InjectMocks
    private FreePostController freePostController;

    @Mock
    private FreePostCommandService freePostCommandService;

    @Mock
    private FreePostQueryService freePostQueryService;

    @Mock
    private FreePostRankingService freePostRankingService;

    private CustomUserDetails userDetails;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private FreePostResponse sampleResponse() {
        return new FreePostResponse(10L, 1L, "테스터", "제목", "내용", 0, 0,
                LocalDateTime.now(), LocalDateTime.now());
    }

    @BeforeEach
    void setUp() {
        userDetails = new TestCustomUserDetails(1L, "USER");
        mockMvc = MockMvcBuilders.standaloneSetup(freePostController)
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
    @DisplayName("GET /api/v1/posts/free")
    class GetPosts {

        @Test
        @DisplayName("200 성공: 게시글 목록 조회")
        void success() throws Exception {
            given(freePostQueryService.getPosts(any(), any(Pageable.class)))
                    .willReturn(new PageImpl<>(List.of(sampleResponse())));

            mockMvc.perform(get("/api/v1/posts/free"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true));
        }
    }

    @Nested
    @DisplayName("GET /api/v1/posts/free/me")
    class GetMyPosts {

        @Test
        @DisplayName("200 성공: 내 게시글 목록 조회")
        void success() throws Exception {
            given(freePostQueryService.getMyPosts(eq(1L), any(Pageable.class)))
                    .willReturn(new PageImpl<>(List.of(sampleResponse())));

            mockMvc.perform(get("/api/v1/posts/free/me"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true));
        }
    }

    @Nested
    @DisplayName("GET /api/v1/posts/free/{freePostId}")
    class GetPost {

        @Test
        @DisplayName("200 성공: 단건 조회")
        void success() throws Exception {
            given(freePostQueryService.getPost(eq(10L), any(), any()))
                    .willReturn(sampleResponse());

            mockMvc.perform(get("/api/v1/posts/free/10"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true));
        }

        @Test
        @DisplayName("404 실패: 게시글 없음")
        void fail_notFound() throws Exception {
            given(freePostQueryService.getPost(eq(999L), any(), any()))
                    .willThrow(new FreePostException(ErrorCode.FREE_POST_NOT_FOUND));

            mockMvc.perform(get("/api/v1/posts/free/999"))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("FREE_POST_NOT_FOUND"));
        }
    }

    @Nested
    @DisplayName("GET /api/v1/posts/free/popular")
    class GetPopularPosts {

        @Test
        @DisplayName("200 성공: 인기 게시글 조회")
        void success() throws Exception {
            given(freePostRankingService.getPopular(anyInt()))
                    .willReturn(List.of(sampleResponse()));

            mockMvc.perform(get("/api/v1/posts/free/popular"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true));
        }
    }

    @Nested
    @DisplayName("POST /api/v1/posts/free")
    class CreatePost {

        @Test
        @DisplayName("201 성공: 게시글 생성")
        void success() throws Exception {
            CreateFreePostRequest request = new CreateFreePostRequest("제목", "내용");
            given(freePostCommandService.createPost(eq(1L), any(CreateFreePostRequest.class)))
                    .willReturn(sampleResponse());

            mockMvc.perform(post("/api/v1/posts/free")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.success").value(true));
        }

        @Test
        @DisplayName("404 실패: 유저 없음")
        void fail_userNotFound() throws Exception {
            CreateFreePostRequest request = new CreateFreePostRequest("제목", "내용");
            given(freePostCommandService.createPost(eq(1L), any(CreateFreePostRequest.class)))
                    .willThrow(new UserException(ErrorCode.USER_NOT_FOUND));

            mockMvc.perform(post("/api/v1/posts/free")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("USER_NOT_FOUND"));
        }
    }

    @Nested
    @DisplayName("PATCH /api/v1/posts/free/{freePostId}")
    class UpdatePost {

        @Test
        @DisplayName("200 성공: 게시글 수정")
        void success() throws Exception {
            UpdateFreePostRequest request = new UpdateFreePostRequest("수정 제목", "수정 내용");
            given(freePostCommandService.updatePost(eq(10L), eq(1L), any(UpdateFreePostRequest.class)))
                    .willReturn(sampleResponse());

            mockMvc.perform(patch("/api/v1/posts/free/10")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true));
        }

        @Test
        @DisplayName("403 실패: 권한 없음")
        void fail_forbidden() throws Exception {
            UpdateFreePostRequest request = new UpdateFreePostRequest("수정", "수정");
            given(freePostCommandService.updatePost(eq(10L), eq(1L), any(UpdateFreePostRequest.class)))
                    .willThrow(new FreePostException(ErrorCode.USER_FORBIDDEN));

            mockMvc.perform(patch("/api/v1/posts/free/10")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("USER_FORBIDDEN"));
        }
    }

    @Nested
    @DisplayName("DELETE /api/v1/posts/free/{freePostId}")
    class DeletePost {

        @Test
        @DisplayName("200 성공: 게시글 삭제")
        void success() throws Exception {
            willDoNothing().given(freePostCommandService).deletePost(10L, 1L);

            mockMvc.perform(delete("/api/v1/posts/free/10"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true));
        }

        @Test
        @DisplayName("403 실패: 권한 없음")
        void fail_forbidden() throws Exception {
            willThrow(new FreePostException(ErrorCode.USER_FORBIDDEN))
                    .given(freePostCommandService).deletePost(10L, 1L);

            mockMvc.perform(delete("/api/v1/posts/free/10"))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("USER_FORBIDDEN"));
        }
    }
}
