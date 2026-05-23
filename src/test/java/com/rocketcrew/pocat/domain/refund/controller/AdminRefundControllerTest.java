package com.rocketcrew.pocat.domain.refund.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.rocketcrew.pocat.domain.refund.dto.request.RejectRefundRequest;
import com.rocketcrew.pocat.domain.refund.dto.response.AdminRefundResponse;
import com.rocketcrew.pocat.domain.refund.dto.response.RefundResponse;
import com.rocketcrew.pocat.domain.refund.entity.RefundStatus;
import com.rocketcrew.pocat.domain.refund.service.RefundCommandService;
import com.rocketcrew.pocat.domain.refund.service.RefundQueryService;
import com.rocketcrew.pocat.global.exception.common.ErrorCode;
import com.rocketcrew.pocat.global.exception.common.GlobalExceptionHandler;
import com.rocketcrew.pocat.global.exception.domain.RefundException;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class AdminRefundControllerTest {

    private MockMvc mockMvc;

    @InjectMocks
    private AdminRefundController adminRefundController;

    @Mock
    private RefundCommandService refundCommandService;

    @Mock
    private RefundQueryService refundQueryService;

    private CustomUserDetails adminDetails;
    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    @BeforeEach
    void setUp() {
        adminDetails = new TestCustomUserDetails(1L, "ADMIN");
        mockMvc = MockMvcBuilders.standaloneSetup(adminRefundController)
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
                                return adminDetails;
                            }
                        })
                .build();
    }

    private RefundResponse buildRefundResponse(RefundStatus status) {
        return new RefundResponse(1L, 100L, 200L, 10000L,
                "단순 변심", null, status,
                LocalDateTime.now(), LocalDateTime.now());
    }

    @Nested
    @DisplayName("GET /api/v1/admin/refunds")
    class GetAdminRefunds {

        @Test
        @DisplayName("200: 관리자 전체 환불 목록 조회 성공")
        void success_200() throws Exception {
            // given
            AdminRefundResponse item = new AdminRefundResponse(1L, 100L, "buyer_nick",
                    10000L, "단순 변심", RefundStatus.REQUESTED,
                    LocalDateTime.now(), LocalDateTime.now());
            Page<AdminRefundResponse> page = new PageImpl<>(List.of(item), PageRequest.of(0, 20), 1);
            given(refundQueryService.getAdminRefunds(any(), any())).willReturn(page);

            // when & then
            mockMvc.perform(get("/api/v1/admin/refunds"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.content[0].refundId").value(1))
                    .andExpect(jsonPath("$.data.content[0].buyerNickname").value("buyer_nick"))
                    .andExpect(jsonPath("$.data.totalElements").value(1));
        }
    }

    @Nested
    @DisplayName("PATCH /api/v1/admin/refunds/{refundId}/approve")
    class ApproveRefund {

        @Test
        @DisplayName("200: 환불 승인 성공")
        void success_200() throws Exception {
            // given
            RefundResponse response = buildRefundResponse(RefundStatus.COMPLETED);
            given(refundCommandService.approveRefund(1L)).willReturn(response);

            // when & then
            mockMvc.perform(patch("/api/v1/admin/refunds/1/approve"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.status").value("COMPLETED"));
        }

        @Test
        @DisplayName("404: 환불 없음")
        void fail_404_notFound() throws Exception {
            // given
            given(refundCommandService.approveRefund(99L))
                    .willThrow(new RefundException(ErrorCode.REFUND_NOT_FOUND));

            // when & then
            mockMvc.perform(patch("/api/v1/admin/refunds/99/approve"))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("REFUND_NOT_FOUND"));
        }
    }

    @Nested
    @DisplayName("PATCH /api/v1/admin/refunds/{refundId}/reject")
    class RejectRefund {

        @Test
        @DisplayName("200: 환불 거절 성공")
        void success_200() throws Exception {
            // given
            RefundResponse response = new RefundResponse(1L, 100L, 200L, 10000L,
                    "단순 변심", "파손 없음", RefundStatus.REJECTED,
                    LocalDateTime.now(), LocalDateTime.now());
            RejectRefundRequest request = new RejectRefundRequest("파손 없음");
            given(refundCommandService.rejectRefund(eq(1L), any(RejectRefundRequest.class))).willReturn(response);

            // when & then
            mockMvc.perform(patch("/api/v1/admin/refunds/1/reject")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.status").value("REJECTED"))
                    .andExpect(jsonPath("$.data.rejectReason").value("파손 없음"));
        }

        @Test
        @DisplayName("404: 환불 없음")
        void fail_404_notFound() throws Exception {
            // given
            RejectRefundRequest request = new RejectRefundRequest("사유");
            given(refundCommandService.rejectRefund(eq(99L), any(RejectRefundRequest.class)))
                    .willThrow(new RefundException(ErrorCode.REFUND_NOT_FOUND));

            // when & then
            mockMvc.perform(patch("/api/v1/admin/refunds/99/reject")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("REFUND_NOT_FOUND"));
        }
    }
}
