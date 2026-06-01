package com.rocketcrew.pocat.domain.auction.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.rocketcrew.pocat.domain.auction.dto.request.AdminCancelAuctionRequest;
import com.rocketcrew.pocat.domain.auction.dto.request.CreateAuctionRequest;
import com.rocketcrew.pocat.domain.auction.dto.request.InspectAuctionRequest;
import com.rocketcrew.pocat.domain.auction.dto.request.UpdateAuctionRequest;
import com.rocketcrew.pocat.domain.auction.dto.response.AdminAuctionResponse;
import com.rocketcrew.pocat.domain.auction.dto.response.AdminCancelAuctionResponse;
import com.rocketcrew.pocat.domain.auction.dto.response.AuctionResponse;
import com.rocketcrew.pocat.domain.auction.dto.response.CancelAuctionResponse;
import com.rocketcrew.pocat.domain.auction.dto.response.CreateAuctionResponse;
import com.rocketcrew.pocat.domain.auction.dto.response.InspectAuctionResponse;
import com.rocketcrew.pocat.domain.auction.dto.response.SearchAuctionResponse;
import com.rocketcrew.pocat.domain.auction.dto.response.UpdateAuctionResponse;
import com.rocketcrew.pocat.domain.auction.enums.AuctionInspectionResult;
import com.rocketcrew.pocat.domain.auction.enums.AuctionStatus;
import com.rocketcrew.pocat.domain.auction.ranking.service.AuctionRankingService;
import com.rocketcrew.pocat.domain.auction.service.AuctionCommandService;
import com.rocketcrew.pocat.domain.auction.service.AuctionQueryService;
import com.rocketcrew.pocat.domain.card.entity.enums.CardGrade;
import com.rocketcrew.pocat.global.exception.common.ErrorCode;
import com.rocketcrew.pocat.global.exception.common.GlobalExceptionHandler;
import com.rocketcrew.pocat.global.exception.domain.AuctionException;
import com.rocketcrew.pocat.global.ratelimit.RateLimitProperties;
import com.rocketcrew.pocat.global.ratelimit.RedisRateLimiter;
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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.web.PageableHandlerMethodArgumentResolver;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

import java.util.Collections;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class AuctionControllerTest {

    private MockMvc mockMvc;

    @InjectMocks
    AuctionController controller;

    @Mock
    AuctionCommandService commandService;

    @Mock
    AuctionQueryService queryService;

    @Mock
    AuctionRankingService rankingService;

    @Mock
    RedisRateLimiter redisRateLimiter;

    @Mock
    RateLimitProperties rateLimitProperties;

    private CustomUserDetails userDetails;
    private CustomUserDetails adminDetails;

    private final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule());

    @BeforeEach
    void setUp() {
        userDetails = new TestCustomUserDetails(1L, "USER");
        adminDetails = new TestCustomUserDetails(1L, "ADMIN");

        lenient().when(redisRateLimiter.isAllowed(anyString(), anyInt(), anyLong())).thenReturn(true);

        mockMvc = MockMvcBuilders.standaloneSetup(controller)
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

    // ---------------------------------------------------------------
    // GET /api/v1/auctions/popular
    // ---------------------------------------------------------------
    @Nested
    @DisplayName("GET /api/v1/auctions/popular")
    class GetPopularAuctions {

        @Test
        @DisplayName("성공: 인기 경매 목록 반환")
        void success() throws Exception {
            SearchAuctionResponse resp = new SearchAuctionResponse(
                    1L, 2L, "판매자", "리자몽 경매", 1L, "리자몽",
                    CardGrade.PSA_10, null, 10000L, null, 100000L,
                    AuctionStatus.ACTIVE, null, null, null);
            given(rankingService.getPopular(anyInt())).willReturn(List.of(resp));

            mockMvc.perform(get("/api/v1/auctions/popular"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data[0].auctionId").value(1));
        }

        @Test
        @DisplayName("성공: 빈 목록 반환")
        void successEmpty() throws Exception {
            given(rankingService.getPopular(anyInt())).willReturn(Collections.emptyList());

            mockMvc.perform(get("/api/v1/auctions/popular"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data").isArray());
        }
    }

    // ---------------------------------------------------------------
    // GET /api/v1/auctions
    // ---------------------------------------------------------------
    @Nested
    @DisplayName("GET /api/v1/auctions")
    class GetAuctions {

        @Test
        @DisplayName("실패: Rate Limit 초과 → 429 Too Many Requests")
        void fail_429_rateLimitExceeded() throws Exception {
            given(redisRateLimiter.isAllowed(anyString(), anyInt(), anyLong())).willReturn(false);

            mockMvc.perform(get("/api/v1/auctions"))
                    .andExpect(status().isTooManyRequests());
            verifyNoInteractions(queryService);
        }

        @Test
        @DisplayName("성공: 경매 목록 반환")
        void success() throws Exception {
            SearchAuctionResponse resp = new SearchAuctionResponse(
                    1L, 2L, "판매자", "리자몽 경매", 1L, "리자몽",
                    CardGrade.PSA_10, null, 10000L, null, 100000L,
                    AuctionStatus.ACTIVE, null, null, null);
            Page<SearchAuctionResponse> page = new PageImpl<>(List.of(resp), PageRequest.of(0, 20), 1);
            given(queryService.getAuctions(any(), any(), any(), any(), any(), any(), any())).willReturn(page);

            mockMvc.perform(get("/api/v1/auctions"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.content[0].auctionId").value(1));
        }

        @Test
        @DisplayName("성공: 결과 없으면 빈 페이지 반환")
        void successEmpty() throws Exception {
            Page<SearchAuctionResponse> empty = new PageImpl<>(Collections.emptyList(), PageRequest.of(0, 20), 0);
            given(queryService.getAuctions(any(), any(), any(), any(), any(), any(), any())).willReturn(empty);

            mockMvc.perform(get("/api/v1/auctions"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.content").isArray());
        }
    }

    // ---------------------------------------------------------------
    // GET /api/v1/admin/auctions
    // ---------------------------------------------------------------
    @Nested
    @DisplayName("GET /api/v1/admin/auctions")
    class GetAdminAuctions {

        @Test
        @DisplayName("성공: 관리자 경매 목록 반환")
        void success() throws Exception {
            AdminAuctionResponse resp = new AdminAuctionResponse(
                    1L, 2L, "판매자", "리자몽 경매", 1L, "리자몽",
                    CardGrade.PSA_10, null, 10000L, null, 100000L,
                    AuctionStatus.PENDING, null, null);
            Page<AdminAuctionResponse> page = new PageImpl<>(List.of(resp), PageRequest.of(0, 20), 1);
            given(queryService.getAdminAuctions(any(), any(), any(), any(), any(), any(), any())).willReturn(page);

            mockMvc.perform(get("/api/v1/admin/auctions"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.content[0].auctionId").value(1));
        }

        @Test
        @DisplayName("성공: 빈 목록 반환")
        void successEmpty() throws Exception {
            Page<AdminAuctionResponse> empty = new PageImpl<>(Collections.emptyList(), PageRequest.of(0, 20), 0);
            given(queryService.getAdminAuctions(any(), any(), any(), any(), any(), any(), any())).willReturn(empty);

            mockMvc.perform(get("/api/v1/admin/auctions"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.totalElements").value(0));
        }
    }

    // ---------------------------------------------------------------
    // GET /api/v1/auctions/me
    // ---------------------------------------------------------------
    @Nested
    @DisplayName("GET /api/v1/auctions/me")
    class GetMyAuctions {

        @Test
        @DisplayName("성공: 내 경매 목록 반환")
        void success() throws Exception {
            SearchAuctionResponse resp = new SearchAuctionResponse(
                    1L, 1L, "판매자", "리자몽 경매", 1L, "리자몽",
                    CardGrade.PSA_10, null, 10000L, null, 100000L,
                    AuctionStatus.ACTIVE, null, null, null);
            Page<SearchAuctionResponse> page = new PageImpl<>(List.of(resp), PageRequest.of(0, 20), 1);
            given(queryService.getMyAuctions(anyLong(), any(), any())).willReturn(page);

            mockMvc.perform(get("/api/v1/auctions/me"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.content[0].auctionId").value(1));
        }

        @Test
        @DisplayName("성공: 빈 목록 반환")
        void successEmpty() throws Exception {
            Page<SearchAuctionResponse> empty = new PageImpl<>(Collections.emptyList(), PageRequest.of(0, 20), 0);
            given(queryService.getMyAuctions(anyLong(), any(), any())).willReturn(empty);

            mockMvc.perform(get("/api/v1/auctions/me"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.totalElements").value(0));
        }
    }

    // ---------------------------------------------------------------
    // GET /api/v1/auctions/{auctionId}
    // ---------------------------------------------------------------
    @Nested
    @DisplayName("GET /api/v1/auctions/{auctionId}")
    class GetAuction {

        @Test
        @DisplayName("성공: 경매 상세 조회")
        void success() throws Exception {
            AuctionResponse resp = new AuctionResponse(
                    1L, 2L, "판매자", "리자몽 경매", "설명",
                    1L, "리자몽", CardGrade.PSA_10, null,
                    10000L, 100000L, null, null, null,
                    AuctionStatus.ACTIVE, null, null, null,
                    null, null, null, 5L, false);
            given(queryService.getAuction(eq(1L), anyLong())).willReturn(resp);

            mockMvc.perform(get("/api/v1/auctions/1"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.auctionId").value(1));
        }

        @Test
        @DisplayName("실패: 존재하지 않는 경매 → 404")
        void failNotFound() throws Exception {
            given(queryService.getAuction(eq(999L), anyLong()))
                    .willThrow(new AuctionException(ErrorCode.AUCTION_NOT_FOUND));

            mockMvc.perform(get("/api/v1/auctions/999"))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("AUCTION_NOT_FOUND"));
        }
    }

    // ---------------------------------------------------------------
    // POST /api/v1/auctions
    // ---------------------------------------------------------------
    @Nested
    @DisplayName("POST /api/v1/auctions")
    class CreateAuction {

        @Test
        @DisplayName("성공: 경매 생성 201 반환")
        void success() throws Exception {
            CreateAuctionRequest request = new CreateAuctionRequest(1L, "리자몽 경매", "설명", 10000L, 100000L);
            CreateAuctionResponse resp = new CreateAuctionResponse(1L, "리자몽 경매", AuctionStatus.PENDING);
            given(commandService.createAuction(anyLong(), any())).willReturn(resp);

            mockMvc.perform(post("/api/v1/auctions")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.data.auctionId").value(1))
                    .andExpect(jsonPath("$.data.status").value("PENDING"));
        }

        @Test
        @DisplayName("실패: 제목 누락 → 400")
        void failValidation() throws Exception {
            CreateAuctionRequest request = new CreateAuctionRequest(1L, "", "설명", 10000L, 100000L);

            mockMvc.perform(post("/api/v1/auctions")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest());
        }
    }

    // ---------------------------------------------------------------
    // PATCH /api/v1/auctions/{auctionId}
    // ---------------------------------------------------------------
    @Nested
    @DisplayName("PATCH /api/v1/auctions/{auctionId}")
    class UpdateAuction {

        @Test
        @DisplayName("성공: 경매 수정 200 반환")
        void success() throws Exception {
            UpdateAuctionRequest request = new UpdateAuctionRequest("새제목", null, null, null);
            UpdateAuctionResponse resp = new UpdateAuctionResponse(1L, "새제목", "설명", 10000L, 100000L, AuctionStatus.PENDING);
            given(commandService.updateAuction(anyLong(), anyLong(), any())).willReturn(resp);

            mockMvc.perform(patch("/api/v1/auctions/1")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.title").value("새제목"));
        }

        @Test
        @DisplayName("실패: 경매 미존재 → 404")
        void failNotFound() throws Exception {
            UpdateAuctionRequest request = new UpdateAuctionRequest("새제목", null, null, null);
            given(commandService.updateAuction(anyLong(), anyLong(), any()))
                    .willThrow(new AuctionException(ErrorCode.AUCTION_NOT_FOUND));

            mockMvc.perform(patch("/api/v1/auctions/999")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("AUCTION_NOT_FOUND"));
        }
    }

    // ---------------------------------------------------------------
    // PATCH /api/v1/auctions/{auctionId}/cancel
    // ---------------------------------------------------------------
    @Nested
    @DisplayName("PATCH /api/v1/auctions/{auctionId}/cancel")
    class CancelAuction {

        @Test
        @DisplayName("성공: 경매 취소 200 반환")
        void success() throws Exception {
            CancelAuctionResponse resp = new CancelAuctionResponse(1L, AuctionStatus.CANCELLED);
            given(commandService.cancelAuction(anyLong(), anyLong())).willReturn(resp);

            mockMvc.perform(patch("/api/v1/auctions/1/cancel"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.status").value("CANCELLED"));
        }

        @Test
        @DisplayName("실패: PENDING이 아닌 경매 취소 → 409")
        void failNotPending() throws Exception {
            given(commandService.cancelAuction(anyLong(), anyLong()))
                    .willThrow(new AuctionException(ErrorCode.AUCTION_NOT_PENDING));

            mockMvc.perform(patch("/api/v1/auctions/1/cancel"))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("AUCTION_NOT_PENDING"));
        }
    }

    // ---------------------------------------------------------------
    // PATCH /api/v1/admin/auctions/{auctionId}/inspection
    // ---------------------------------------------------------------
    @Nested
    @DisplayName("PATCH /api/v1/admin/auctions/{auctionId}/inspection")
    class InspectAuction {

        @Test
        @DisplayName("성공: 검수 승인 200 반환")
        void success() throws Exception {
            InspectAuctionRequest request = new InspectAuctionRequest(AuctionInspectionResult.PASSED, null);
            InspectAuctionResponse resp = new InspectAuctionResponse(1L, AuctionStatus.APPROVED, null, null, 1L);
            given(commandService.inspectAuction(anyLong(), anyLong(), any())).willReturn(resp);

            mockMvc.perform(patch("/api/v1/admin/auctions/1/inspection")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.status").value("APPROVED"));
        }

        @Test
        @DisplayName("실패: 검수 불가 상태 → 409")
        void failNotInspecting() throws Exception {
            InspectAuctionRequest request = new InspectAuctionRequest(AuctionInspectionResult.PASSED, null);
            given(commandService.inspectAuction(anyLong(), anyLong(), any()))
                    .willThrow(new AuctionException(ErrorCode.AUCTION_INSPECTION_NOT_ALLOWED));

            mockMvc.perform(patch("/api/v1/admin/auctions/1/inspection")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("AUCTION_INSPECTION_NOT_ALLOWED"));
        }
    }

    // ---------------------------------------------------------------
    // PATCH /api/v1/admin/auctions/{auctionId}/cancel
    // ---------------------------------------------------------------
    @Nested
    @DisplayName("PATCH /api/v1/admin/auctions/{auctionId}/cancel")
    class AdminCancelAuction {

        @Test
        @DisplayName("성공: 관리자 취소 200 반환")
        void success() throws Exception {
            AdminCancelAuctionRequest request = new AdminCancelAuctionRequest("정책 위반");
            AdminCancelAuctionResponse resp = new AdminCancelAuctionResponse(1L, AuctionStatus.CANCELLED, "정책 위반");
            given(commandService.adminCancelAuction(anyLong(), anyLong(), any())).willReturn(resp);

            mockMvc.perform(patch("/api/v1/admin/auctions/1/cancel")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.status").value("CANCELLED"))
                    .andExpect(jsonPath("$.data.reason").value("정책 위반"));
        }

        @Test
        @DisplayName("실패: 취소 불가 상태 → 409")
        void failCannotCancel() throws Exception {
            AdminCancelAuctionRequest request = new AdminCancelAuctionRequest("정책 위반");
            given(commandService.adminCancelAuction(anyLong(), anyLong(), any()))
                    .willThrow(new AuctionException(ErrorCode.AUCTION_CANNOT_CANCEL));

            mockMvc.perform(patch("/api/v1/admin/auctions/1/cancel")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("AUCTION_CANNOT_CANCEL"));
        }
    }
}
