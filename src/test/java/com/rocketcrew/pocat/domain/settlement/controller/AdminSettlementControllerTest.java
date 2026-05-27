package com.rocketcrew.pocat.domain.settlement.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.rocketcrew.pocat.domain.settlement.dto.response.AdminSettlementResponse;
import com.rocketcrew.pocat.domain.settlement.dto.response.SettlementCompleteResponse;
import com.rocketcrew.pocat.domain.settlement.enums.SettlementStatus;
import com.rocketcrew.pocat.domain.settlement.service.AdminSettlementCommandService;
import com.rocketcrew.pocat.domain.settlement.service.AdminSettlementQueryService;
import com.rocketcrew.pocat.global.exception.common.ErrorCode;
import com.rocketcrew.pocat.global.exception.common.GlobalExceptionHandler;
import com.rocketcrew.pocat.global.exception.domain.SettlementException;
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
class AdminSettlementControllerTest {

    private MockMvc mockMvc;

    @InjectMocks
    private AdminSettlementController adminSettlementController;

    @Mock
    private AdminSettlementCommandService adminSettlementCommandService;

    @Mock
    private AdminSettlementQueryService adminSettlementQueryService;

    private CustomUserDetails adminDetails;
    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    @BeforeEach
    void setUp() {
        adminDetails = new TestCustomUserDetails(1L, "ADMIN");
        mockMvc = MockMvcBuilders.standaloneSetup(adminSettlementController)
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

    @Nested
    @DisplayName("GET /api/v1/admin/settlements")
    class GetAdminSettlements {

        @Test
        @DisplayName("200: 관리자 정산 목록 조회 성공")
        void success_200() throws Exception {
            // given
            AdminSettlementResponse item = new AdminSettlementResponse(
                    "SET-001", "ORD-001", "seller_nick", "신한은행", "110-123-456789",
                    "피카츄", "PSA_10", 10000L, 500L, 9500L,
                    SettlementStatus.PENDING, null, LocalDateTime.now()
            );
            Page<AdminSettlementResponse> page = new PageImpl<>(List.of(item), PageRequest.of(0, 20), 1);
            given(adminSettlementQueryService.getAdminSettlements(any(), any())).willReturn(page);

            // when & then
            mockMvc.perform(get("/api/v1/admin/settlements"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.content[0].settlementUid").value("SET-001"))
                    .andExpect(jsonPath("$.data.content[0].status").value("PENDING"))
                    .andExpect(jsonPath("$.data.totalElements").value(1));
        }
    }

    @Nested
    @DisplayName("PATCH /api/v1/admin/settlements/{settlementUid}/complete")
    class CompleteSettlement {

        @Test
        @DisplayName("200: 정산 완료 처리 성공")
        void success_200() throws Exception {
            // given
            SettlementCompleteResponse response = new SettlementCompleteResponse(
                    "SET-001", SettlementStatus.COMPLETED, LocalDateTime.now()
            );
            given(adminSettlementCommandService.completeSettlement("SET-001")).willReturn(response);

            // when & then
            mockMvc.perform(patch("/api/v1/admin/settlements/SET-001/complete"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.settlementUid").value("SET-001"))
                    .andExpect(jsonPath("$.data.status").value("COMPLETED"));
        }

        @Test
        @DisplayName("404: 정산 없음")
        void fail_404_notFound() throws Exception {
            // given
            given(adminSettlementCommandService.completeSettlement("NOT-EXIST"))
                    .willThrow(new SettlementException(ErrorCode.SETTLEMENT_NOT_FOUND));

            // when & then
            mockMvc.perform(patch("/api/v1/admin/settlements/NOT-EXIST/complete"))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("SETTLEMENT_NOT_FOUND"));
        }
    }
}
