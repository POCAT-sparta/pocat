package com.rocketcrew.pocat.domain.settlement.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.rocketcrew.pocat.domain.settlement.dto.response.SettlementResponse;
import com.rocketcrew.pocat.domain.settlement.enums.SettlementStatus;
import com.rocketcrew.pocat.domain.settlement.service.SettlementQueryService;
import com.rocketcrew.pocat.global.exception.common.ErrorCode;
import com.rocketcrew.pocat.global.exception.common.GlobalExceptionHandler;
import com.rocketcrew.pocat.global.exception.domain.SettlementException;
import com.rocketcrew.pocat.global.security.CustomUserDetails;
import com.rocketcrew.pocat.support.TestCustomUserDetails;
import com.rocketcrew.pocat.domain.card.entity.enums.CardGrade;
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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class SettlementControllerTest {

    private MockMvc mockMvc;

    @InjectMocks
    private SettlementController settlementController;

    @Mock
    private SettlementQueryService settlementQueryService;

    private CustomUserDetails userDetails;
    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    @BeforeEach
    void setUp() {
        userDetails = new TestCustomUserDetails(1L, "USER");
        mockMvc = MockMvcBuilders.standaloneSetup(settlementController)
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

    private SettlementResponse buildSettlementResponse() {
        return new SettlementResponse(
                "SET-001", "ORD-001", "피카츄", CardGrade.PSA_10,
                "http://example.com/card.png", 10000L, 500L, 9500L,
                SettlementStatus.PENDING, null, LocalDateTime.now()
        );
    }

    @Nested
    @DisplayName("GET /api/v1/settlements/me")
    class GetSettlements {

        @Test
        @DisplayName("200: 내 정산 목록 조회 성공")
        void success_200() throws Exception {
            // given
            SettlementResponse item = buildSettlementResponse();
            Page<SettlementResponse> page = new PageImpl<>(List.of(item), PageRequest.of(0, 20), 1);
            given(settlementQueryService.getSettlements(eq(1L), any())).willReturn(page);

            // when & then
            mockMvc.perform(get("/api/v1/settlements/me"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.status").value(200))
                    .andExpect(jsonPath("$.data.content[0].settlementUid").value("SET-001"))
                    .andExpect(jsonPath("$.data.content[0].totalPrice").value(10000))
                    .andExpect(jsonPath("$.data.totalElements").value(1));
        }
    }

    @Nested
    @DisplayName("GET /api/v1/settlements/{settlementUid}")
    class GetOneSettlement {

        @Test
        @DisplayName("200: 단건 정산 조회 성공")
        void success_200() throws Exception {
            // given
            SettlementResponse response = buildSettlementResponse();
            given(settlementQueryService.getOneSettlement(eq(1L), eq("SET-001"))).willReturn(response);

            // when & then
            mockMvc.perform(get("/api/v1/settlements/SET-001"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.settlementUid").value("SET-001"))
                    .andExpect(jsonPath("$.data.platformFee").value(500))
                    .andExpect(jsonPath("$.data.sellerAmount").value(9500));
        }

        @Test
        @DisplayName("404: 정산 없음")
        void fail_404_notFound() throws Exception {
            // given
            given(settlementQueryService.getOneSettlement(eq(1L), eq("NOT-EXIST")))
                    .willThrow(new SettlementException(ErrorCode.SETTLEMENT_NOT_FOUND));

            // when & then
            mockMvc.perform(get("/api/v1/settlements/NOT-EXIST"))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("SETTLEMENT_NOT_FOUND"));
        }
    }
}
