package com.rocketcrew.pocat.domain.auction.controller;

import com.rocketcrew.pocat.domain.auction.service.AuctionBuyoutService;
import com.rocketcrew.pocat.domain.refund.controller.InternalRefundController;
import com.rocketcrew.pocat.domain.refund.service.RefundCommandService;
import com.rocketcrew.pocat.global.config.SecurityConfig;
import com.rocketcrew.pocat.global.security.JwtUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.security.test.context.support.WithAnonymousUser;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Internal API 엔드포인트 MVC 테스트
 *
 * 테스트 대상:
 * - /internal/auctions/{id}/recover-buyout (InternalAuctionController)
 * - /internal/refunds/{id}/retry (InternalRefundController)
 *
 * 검증 항목:
 * - X-Internal-Token 헤더 검증 (401 Unauthorized)
 * - 토큰 값 검증 (401 Unauthorized)
 * - 정상 처리 (200 OK)
 * - 예외 처리 (500 Internal Server Error)
 */
@WebMvcTest(controllers = {InternalAuctionController.class, InternalRefundController.class})
@TestPropertySource(properties = "pocat.internal.token=test-token")
class InternalEndpointMvcTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AuctionBuyoutService auctionBuyoutService;

    @MockBean
    private RefundCommandService refundCommandService;

    @MockBean
    private JwtUtil jwtUtil;

    @MockBean
    private StringRedisTemplate stringRedisTemplate;

    // 테스트할 경매 ID와 환불 ID
    private static final Long AUCTION_ID = 1L;
    private static final Long REFUND_ID = 1L;
    private static final String VALID_TOKEN = "test-token";
    private static final String INVALID_TOKEN = "wrong-token";

    @Nested
    @DisplayName("InternalAuctionController - /internal/auctions/{id}/recover-buyout")
    class InternalAuctionControllerTests {

        @BeforeEach
        void setUp() {
            // 기본 설정: 성공 케이스
            given(auctionBuyoutService.recoverStalePaymentPendingAuction(anyLong()))
                    .willReturn(true);
        }

        @Test
        @DisplayName("T-01: X-Internal-Token 헤더 없을 때 400 반환")
        void shouldReturn400WhenTokenMissing_auction() throws Exception {
            mockMvc.perform(post("/internal/auctions/{id}/recover-buyout", AUCTION_ID)
)
                    .andDo(print())
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("T-02: X-Internal-Token이 유효하지 않을 때 401 반환")
        void shouldReturn401WhenTokenInvalid_auction() throws Exception {
            mockMvc.perform(post("/internal/auctions/{id}/recover-buyout", AUCTION_ID)
                    .header("X-Internal-Token", INVALID_TOKEN)
)
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("T-03: 유효한 토큰으로 요청하면 200 반환 (복구 성공)")
        void shouldReturn200WhenAuthorized_auction_recovered() throws Exception {
            given(auctionBuyoutService.recoverStalePaymentPendingAuction(AUCTION_ID))
                    .willReturn(true);

            mockMvc.perform(post("/internal/auctions/{id}/recover-buyout", AUCTION_ID)
                    .header("X-Internal-Token", VALID_TOKEN)
)
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("T-04: 유효한 토큰으로 요청하면 200 반환 (복구 스킵 - 이미 처리됨)")
        void shouldReturn200WhenAuthorized_auction_skipped() throws Exception {
            given(auctionBuyoutService.recoverStalePaymentPendingAuction(AUCTION_ID))
                    .willReturn(false);

            mockMvc.perform(post("/internal/auctions/{id}/recover-buyout", AUCTION_ID)
                    .header("X-Internal-Token", VALID_TOKEN)
)
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("T-05: 유효한 토큰이지만 IllegalStateException 발생 시 200 반환 (멱등성)")
        void shouldReturn200WhenIllegalStateException_auction() throws Exception {
            willThrow(new IllegalStateException("이미 처리됨"))
                    .given(auctionBuyoutService).recoverStalePaymentPendingAuction(AUCTION_ID);

            mockMvc.perform(post("/internal/auctions/{id}/recover-buyout", AUCTION_ID)
                    .header("X-Internal-Token", VALID_TOKEN)
)
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("T-06: 유효한 토큰이지만 예상치 못한 예외 발생 시 500 반환")
        void shouldReturn500OnServerError_auction() throws Exception {
            willThrow(new RuntimeException("Database connection failed"))
                    .given(auctionBuyoutService).recoverStalePaymentPendingAuction(AUCTION_ID);

            mockMvc.perform(post("/internal/auctions/{id}/recover-buyout", AUCTION_ID)
                    .header("X-Internal-Token", VALID_TOKEN))
                    .andExpect(status().isInternalServerError());
        }
    }

    @Nested
    @DisplayName("InternalRefundController - /internal/refunds/{id}/retry")
    class InternalRefundControllerTests {

        @BeforeEach
        void setUp() {
            // 기본 설정: 성공 케이스
            // refundCommandService.retryRefund()는 void 메서드
        }

        @Test
        @DisplayName("T-07: X-Internal-Token 헤더 없을 때 400 반환")
        void shouldReturn400WhenTokenMissing_refund() throws Exception {
            mockMvc.perform(post("/internal/refunds/{id}/retry", REFUND_ID)
)
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("T-08: X-Internal-Token이 유효하지 않을 때 401 반환")
        void shouldReturn401WhenTokenInvalid_refund() throws Exception {
            mockMvc.perform(post("/internal/refunds/{id}/retry", REFUND_ID)
                    .header("X-Internal-Token", INVALID_TOKEN)
)
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("T-09: 유효한 토큰으로 요청하면 200 반환 (재시도 성공)")
        void shouldReturn200WhenAuthorized_refund() throws Exception {
            mockMvc.perform(post("/internal/refunds/{id}/retry", REFUND_ID)
                    .header("X-Internal-Token", VALID_TOKEN))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("T-10: 유효한 토큰이지만 IllegalStateException 발생 시 200 반환 (멱등성)")
        void shouldReturn200WhenIllegalStateException_refund() throws Exception {
            willThrow(new IllegalStateException("이미 처리됨"))
                    .given(refundCommandService).retryRefund(REFUND_ID);

            mockMvc.perform(post("/internal/refunds/{id}/retry", REFUND_ID)
                    .header("X-Internal-Token", VALID_TOKEN))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("T-11: 유효한 토큰이지만 예상치 못한 예외 발생 시 500 반환")
        void shouldReturn500OnServerError_refund() throws Exception {
            willThrow(new RuntimeException("Payment gateway unavailable"))
                    .given(refundCommandService).retryRefund(REFUND_ID);

            mockMvc.perform(post("/internal/refunds/{id}/retry", REFUND_ID)
                    .header("X-Internal-Token", VALID_TOKEN))
                    .andExpect(status().isInternalServerError());
        }

        @Test
        @DisplayName("T-12: 유효한 토큰이지만 IllegalArgumentException 발생 시 200 반환 (멱등성)")
        void shouldReturn200WhenIllegalArgumentException_refund() throws Exception {
            willThrow(new IllegalArgumentException("invalid refund state"))
                    .given(refundCommandService).retryRefund(REFUND_ID);

            mockMvc.perform(post("/internal/refunds/{id}/retry", REFUND_ID)
                    .header("X-Internal-Token", VALID_TOKEN))
                    .andExpect(status().isOk());
        }
    }
}
