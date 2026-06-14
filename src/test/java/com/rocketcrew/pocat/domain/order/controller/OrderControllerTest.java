package com.rocketcrew.pocat.domain.order.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.rocketcrew.pocat.domain.order.dto.response.OrderDetailResponse;
import com.rocketcrew.pocat.domain.order.dto.response.OrderResponse;
import com.rocketcrew.pocat.domain.order.enums.OrderStatus;
import com.rocketcrew.pocat.domain.order.enums.OrderType;
import com.rocketcrew.pocat.domain.order.service.OrderCommandService;
import com.rocketcrew.pocat.domain.order.service.OrderQueryService;
import com.rocketcrew.pocat.global.exception.common.ErrorCode;
import com.rocketcrew.pocat.global.exception.common.GlobalExceptionHandler;
import com.rocketcrew.pocat.global.exception.domain.OrderException;
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
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("OrderController")
class OrderControllerTest {

    private MockMvc mockMvc;

    @InjectMocks
    private OrderController orderController;

    @Mock
    private OrderQueryService orderQueryService;

    @Mock
    private OrderCommandService orderCommandService;

    @Mock
    private RedisRateLimiter redisRateLimiter;

    @Mock
    private RateLimitProperties rateLimitProperties;

    private CustomUserDetails userDetails;
    private final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule());

    @BeforeEach
    void setUp() {
        userDetails = new TestCustomUserDetails(1L, "USER");
        given(redisRateLimiter.isAllowed(anyString(), anyInt(), anyLong())).willReturn(true);
        mockMvc = MockMvcBuilders.standaloneSetup(orderController)
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

    private OrderResponse sampleOrderResponse() {
        return new OrderResponse(
                1L, "ORD-001", 10L, "피카츄", "PSA_10",
                "https://images.pocat.io/pikachu.jpg",
                10000L, OrderStatus.PAYMENT_COMPLETED.name(),
                OrderType.AUCTION.name(),
                null,
                LocalDateTime.now());
    }

    private OrderDetailResponse sampleOrderDetailResponse() {
        return new OrderDetailResponse(
                "ORD-001",
                10L,
                new OrderDetailResponse.UserInfo("구매자"),
                new OrderDetailResponse.UserInfo("판매자"),
                new OrderDetailResponse.CardInfo("피카츄", "PSA_10",
                        "https://images.pocat.io/pikachu.jpg"),
                10000L,
                OrderStatus.PAYMENT_COMPLETED.name(),
                null,
                null,
                LocalDateTime.now(),
                LocalDateTime.now()
        );
    }

    // ── GET /api/v1/orders/me ──────────────────────────────────────────

    @Nested
    @DisplayName("GET /api/v1/orders/me")
    class GetMyOrders {

        @Test
        @DisplayName("성공: 200 OK 와 함께 페이지 응답을 반환한다")
        void success_200() throws Exception {
            Page<OrderResponse> page = new PageImpl<>(
                    List.of(sampleOrderResponse()), PageRequest.of(0, 20), 1);

            given(orderQueryService.getMyOrders(eq(1L), isNull(), any(Pageable.class)))
                    .willReturn(page);

            mockMvc.perform(get("/api/v1/orders/me"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.content[0].orderUid").value("ORD-001"))
                    .andExpect(jsonPath("$.data.totalElements").value(1));
        }
    }

    // ── GET /api/v1/orders/{orderUid} ─────────────────────────────────

    @Nested
    @DisplayName("GET /api/v1/orders/{orderUid}")
    class GetOneOrder {

        @Test
        @DisplayName("성공: 200 OK 와 함께 주문 상세를 반환한다")
        void success_200() throws Exception {
            given(orderQueryService.getOneOrder(1L, "ORD-001"))
                    .willReturn(sampleOrderDetailResponse());

            mockMvc.perform(get("/api/v1/orders/ORD-001"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.orderUid").value("ORD-001"))
                    .andExpect(jsonPath("$.data.buyer.nickname").value("구매자"));
        }

        @Test
        @DisplayName("실패: 404 — 주문 없음")
        void fail_404_notFound() throws Exception {
            given(orderQueryService.getOneOrder(1L, "UNKNOWN"))
                    .willThrow(new OrderException(ErrorCode.ORDER_NOT_FOUND));

            mockMvc.perform(get("/api/v1/orders/UNKNOWN"))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("ORDER_NOT_FOUND"));
        }
    }
}
