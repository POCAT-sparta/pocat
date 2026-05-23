package com.rocketcrew.pocat.domain.order.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rocketcrew.pocat.domain.order.dto.request.AdminOrderSearchCondition;
import com.rocketcrew.pocat.domain.order.dto.response.AdminOrderResponse;
import com.rocketcrew.pocat.domain.order.enums.DeliveryStatus;
import com.rocketcrew.pocat.domain.order.enums.OrderStatus;
import com.rocketcrew.pocat.domain.order.service.AdminOrderQueryService;
import com.rocketcrew.pocat.global.exception.common.GlobalExceptionHandler;
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
import org.springframework.data.domain.Pageable;
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

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("AdminOrderController")
class AdminOrderControllerTest {

    private MockMvc mockMvc;

    @InjectMocks
    private AdminOrderController adminOrderController;

    @Mock
    private AdminOrderQueryService adminOrderQueryService;

    private CustomUserDetails adminDetails;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        adminDetails = new TestCustomUserDetails(2L, "ADMIN");
        mockMvc = MockMvcBuilders.standaloneSetup(adminOrderController)
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

    private AdminOrderResponse sampleAdminOrderResponse() {
        return new AdminOrderResponse(
                1L, "ORD-001",
                "구매자", "판매자",
                "피카츄", "PSA_10",
                10000L,
                OrderStatus.PAYMENT_COMPLETED,
                DeliveryStatus.PREPARING,
                LocalDateTime.now()
        );
    }

    // ── GET /api/v1/admin/orders ───────────────────────────────────────

    @Nested
    @DisplayName("GET /api/v1/admin/orders")
    class GetAdminOrders {

        @Test
        @DisplayName("성공: 200 OK 와 함께 관리자 주문 목록을 반환한다")
        void success_200() throws Exception {
            Page<AdminOrderResponse> page = new PageImpl<>(
                    List.of(sampleAdminOrderResponse()), PageRequest.of(0, 20), 1);

            given(adminOrderQueryService.getAdminOrders(
                    any(AdminOrderSearchCondition.class), any(Pageable.class)))
                    .willReturn(page);

            mockMvc.perform(get("/api/v1/admin/orders"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.content[0].orderUid").value("ORD-001"))
                    .andExpect(jsonPath("$.data.totalElements").value(1));
        }

        @Test
        @DisplayName("성공: 상태 필터를 쿼리 파라미터로 전달해도 200 OK 반환한다")
        void success_withStatusFilter() throws Exception {
            Page<AdminOrderResponse> page = new PageImpl<>(
                    List.of(sampleAdminOrderResponse()), PageRequest.of(0, 20), 1);

            given(adminOrderQueryService.getAdminOrders(
                    any(AdminOrderSearchCondition.class), any(Pageable.class)))
                    .willReturn(page);

            mockMvc.perform(get("/api/v1/admin/orders")
                            .param("orderStatus", "PAYMENT_COMPLETED"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.content[0].status").value("PAYMENT_COMPLETED"));
        }
    }
}
