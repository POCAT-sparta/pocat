package com.rocketcrew.pocat.domain.refund.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.rocketcrew.pocat.domain.refund.dto.request.CreateRefundRequest;
import com.rocketcrew.pocat.domain.refund.dto.response.RefundResponse;
import com.rocketcrew.pocat.domain.refund.entity.RefundStatus;
import com.rocketcrew.pocat.domain.refund.service.RefundCommandService;
import com.rocketcrew.pocat.domain.refund.service.RefundQueryService;
import com.rocketcrew.pocat.global.exception.common.ErrorCode;
import com.rocketcrew.pocat.global.exception.common.GlobalExceptionHandler;
import com.rocketcrew.pocat.global.exception.domain.RefundException;
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
import org.springframework.core.MethodParameter;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class RefundControllerTest {

    private MockMvc mockMvc;

    @InjectMocks
    private RefundController refundController;

    @Mock
    private RefundCommandService refundCommandService;

    @Mock
    private RefundQueryService refundQueryService;

    @Mock
    private RedisRateLimiter redisRateLimiter;

    @Mock
    private RateLimitProperties rateLimitProperties;

    private CustomUserDetails userDetails;
    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    @BeforeEach
    void setUp() {
        userDetails = new TestCustomUserDetails(1L, "USER");
        given(redisRateLimiter.isAllowed(anyString(), anyInt(), anyLong())).willReturn(true);
        mockMvc = MockMvcBuilders.standaloneSetup(refundController)
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

    private RefundResponse buildRefundResponse() {
        return new RefundResponse(1L, 100L, 200L, 10000L,
                "단순 변심", null, RefundStatus.REQUESTED,
                LocalDateTime.now(), LocalDateTime.now());
    }

    @Nested
    @DisplayName("POST /api/v1/refunds")
    class CreateRefund {

        @Test
        @DisplayName("201: 환불 요청 성공")
        void success_201() throws Exception {
            // given
            CreateRefundRequest request = new CreateRefundRequest(100L, "단순 변심");
            RefundResponse response = buildRefundResponse();
            given(refundCommandService.createRefund(eq(1L), any(CreateRefundRequest.class))).willReturn(response);

            // when & then
            mockMvc.perform(post("/api/v1/refunds")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.status").value(201))
                    .andExpect(jsonPath("$.data.orderId").value(100))
                    .andExpect(jsonPath("$.data.amount").value(10000))
                    .andExpect(jsonPath("$.data.status").value("REQUESTED"));
        }

        @Test
        @DisplayName("409: 동일 주문에 환불 이미 존재")
        void fail_409_duplicateRefund() throws Exception {
            // given
            CreateRefundRequest request = new CreateRefundRequest(100L, "단순 변심");
            given(refundCommandService.createRefund(eq(1L), any(CreateRefundRequest.class)))
                    .willThrow(new RefundException(ErrorCode.REFUND_ALREADY_EXISTS));

            // when & then
            mockMvc.perform(post("/api/v1/refunds")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("REFUND_ALREADY_EXISTS"));
        }
    }

    @Nested
    @DisplayName("GET /api/v1/refunds/me")
    class GetMyRefunds {

        @Test
        @DisplayName("200: 내 환불 목록 조회 성공")
        void success_200() throws Exception {
            // given
            RefundResponse item = buildRefundResponse();
            Page<RefundResponse> page = new PageImpl<>(List.of(item), PageRequest.of(0, 10), 1);
            given(refundQueryService.getMyRefunds(eq(1L), any(), any())).willReturn(page);

            // when & then
            mockMvc.perform(get("/api/v1/refunds/me"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.content[0].refundId").value(1))
                    .andExpect(jsonPath("$.data.totalElements").value(1));
        }
    }

    @Nested
    @DisplayName("GET /api/v1/refunds/{refundId}")
    class GetRefund {

        @Test
        @DisplayName("200: 환불 상세 조회 성공")
        void success_200() throws Exception {
            // given
            RefundResponse response = buildRefundResponse();
            given(refundQueryService.getRefund(eq(1L), eq(1L))).willReturn(response);

            // when & then
            mockMvc.perform(get("/api/v1/refunds/1"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.refundId").value(1))
                    .andExpect(jsonPath("$.data.amount").value(10000));
        }

        @Test
        @DisplayName("403: 구매자 불일치 — 접근 금지")
        void fail_403_forbidden() throws Exception {
            // given
            given(refundQueryService.getRefund(eq(1L), eq(2L)))
                    .willThrow(new RefundException(ErrorCode.REFUND_BUYER_MISMATCH));

            // when & then
            mockMvc.perform(get("/api/v1/refunds/2"))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("REFUND_BUYER_MISMATCH"));
        }
    }
}
