package com.rocketcrew.pocat.domain.auction.controller;

import com.rocketcrew.pocat.domain.auction.service.AuctionBuyoutService;
import com.rocketcrew.pocat.domain.auction.service.AuctionLifecycleService;
import com.rocketcrew.pocat.global.config.SecurityConfig;
import com.rocketcrew.pocat.global.exception.common.ErrorCode;
import com.rocketcrew.pocat.global.exception.domain.AuctionException;
import com.rocketcrew.pocat.global.security.JwtUtil;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * InternalAuctionController 슬라이스 테스트 (RED).
 *
 * <p>대상 엔드포인트:
 * <ul>
 *   <li>POST /internal/auctions/{id}/activate</li>
 *   <li>POST /internal/auctions/{id}/close-expired</li>
 * </ul>
 *
 * <p>{@code /internal/**} 경로는 {@code InternalTokenAuthFilter}에서 X-Internal-Token 헤더를 검증한다.
 */
@WebMvcTest(controllers = InternalAuctionController.class)
@Import(SecurityConfig.class)
@TestPropertySource(properties = {"pocat.internal.token=test-token", "cors.allowed-origins=http://localhost:3000"})
@DisplayName("InternalAuctionController")
class InternalAuctionControllerTest {

    private static final Long AUCTION_ID = 1L;
    private static final String VALID_TOKEN = "test-token";
    private static final String INVALID_TOKEN = "wrong-token";

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AuctionBuyoutService auctionBuyoutService;

    @MockBean
    private AuctionLifecycleService auctionLifecycleService;

    @MockBean
    private JwtUtil jwtUtil;

    @MockBean
    private StringRedisTemplate stringRedisTemplate;

    @Nested
    @DisplayName("POST /internal/auctions/{id}/activate")
    class Activate {

        private static final String URL = "/internal/auctions/{id}/activate";

        @Test
        @Tag("integration")
        @DisplayName("X-Internal-Token 헤더가 없으면 401 반환")
        void missingToken_returns401() throws Exception {
            mockMvc.perform(post(URL, AUCTION_ID))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @Tag("integration")
        @DisplayName("X-Internal-Token 값이 올바르지 않으면 401 반환")
        void invalidToken_returns401() throws Exception {
            mockMvc.perform(post(URL, AUCTION_ID)
                            .header("X-Internal-Token", INVALID_TOKEN))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("activateApprovedAuction이 true를 반환하면 200과 success(true)를 반환한다")
        void activated_returns200WithSuccessTrue() throws Exception {
            given(auctionLifecycleService.activateApprovedAuction(AUCTION_ID)).willReturn(true);

            mockMvc.perform(post(URL, AUCTION_ID)
                            .header("X-Internal-Token", VALID_TOKEN))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data").value(true));
        }

        @Test
        @DisplayName("activateApprovedAuction이 false를 반환하면 200과 success(false)를 반환한다")
        void notActivated_returns200WithSuccessFalse() throws Exception {
            given(auctionLifecycleService.activateApprovedAuction(AUCTION_ID)).willReturn(false);

            mockMvc.perform(post(URL, AUCTION_ID)
                            .header("X-Internal-Token", VALID_TOKEN))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data").value(false));
        }

        @Test
        @DisplayName("AuctionException(AUCTION_LOCK_FAILED) 발생 시 200과 success(false)를 반환한다 (락충돌 스킵)")
        void lockFailed_returns200WithSuccessFalse() throws Exception {
            willThrow(new AuctionException(ErrorCode.AUCTION_LOCK_FAILED))
                    .given(auctionLifecycleService).activateApprovedAuction(AUCTION_ID);

            mockMvc.perform(post(URL, AUCTION_ID)
                            .header("X-Internal-Token", VALID_TOKEN))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data").value(false));
        }

        @Test
        @DisplayName("AuctionException(AUCTION_NOT_FOUND) 발생 시 200과 success(false)를 반환한다")
        void auctionNotFound_returns200WithSuccessFalse() throws Exception {
            willThrow(new AuctionException(ErrorCode.AUCTION_NOT_FOUND))
                    .given(auctionLifecycleService).activateApprovedAuction(AUCTION_ID);

            mockMvc.perform(post(URL, AUCTION_ID)
                            .header("X-Internal-Token", VALID_TOKEN))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data").value(false));
        }

        @Test
        @DisplayName("그 외 RuntimeException 발생 시 500을 반환한다")
        void unexpectedException_returns500() throws Exception {
            willThrow(new RuntimeException("DB connection failed"))
                    .given(auctionLifecycleService).activateApprovedAuction(AUCTION_ID);

            mockMvc.perform(post(URL, AUCTION_ID)
                            .header("X-Internal-Token", VALID_TOKEN))
                    .andExpect(status().isInternalServerError());
        }

        @Test
        @DisplayName("id가 0 이하이면 400을 반환한다")
        void nonPositiveId_returns400() throws Exception {
            mockMvc.perform(post(URL, 0L)
                            .header("X-Internal-Token", VALID_TOKEN))
                    .andExpect(status().isBadRequest());
        }
    }

    @Nested
    @DisplayName("POST /internal/auctions/{id}/close-expired")
    class CloseExpired {

        private static final String URL = "/internal/auctions/{id}/close-expired";

        @Test
        @Tag("integration")
        @DisplayName("X-Internal-Token 헤더가 없으면 401 반환")
        void missingToken_returns401() throws Exception {
            mockMvc.perform(post(URL, AUCTION_ID))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @Tag("integration")
        @DisplayName("X-Internal-Token 값이 올바르지 않으면 401 반환")
        void invalidToken_returns401() throws Exception {
            mockMvc.perform(post(URL, AUCTION_ID)
                            .header("X-Internal-Token", INVALID_TOKEN))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("closeExpiredAuction이 true를 반환하면 200과 success(true)를 반환한다")
        void closed_returns200WithSuccessTrue() throws Exception {
            given(auctionLifecycleService.closeExpiredAuction(AUCTION_ID)).willReturn(true);

            mockMvc.perform(post(URL, AUCTION_ID)
                            .header("X-Internal-Token", VALID_TOKEN))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data").value(true));
        }

        @Test
        @DisplayName("closeExpiredAuction이 false를 반환하면 200과 success(false)를 반환한다")
        void notClosed_returns200WithSuccessFalse() throws Exception {
            given(auctionLifecycleService.closeExpiredAuction(AUCTION_ID)).willReturn(false);

            mockMvc.perform(post(URL, AUCTION_ID)
                            .header("X-Internal-Token", VALID_TOKEN))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data").value(false));
        }

        @Test
        @DisplayName("AuctionException(AUCTION_LOCK_FAILED) 발생 시 200과 success(false)를 반환한다 (락충돌 스킵)")
        void lockFailed_returns200WithSuccessFalse() throws Exception {
            willThrow(new AuctionException(ErrorCode.AUCTION_LOCK_FAILED))
                    .given(auctionLifecycleService).closeExpiredAuction(AUCTION_ID);

            mockMvc.perform(post(URL, AUCTION_ID)
                            .header("X-Internal-Token", VALID_TOKEN))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data").value(false));
        }

        @Test
        @DisplayName("AuctionException(AUCTION_NOT_FOUND) 발생 시 200과 success(false)를 반환한다")
        void auctionNotFound_returns200WithSuccessFalse() throws Exception {
            willThrow(new AuctionException(ErrorCode.AUCTION_NOT_FOUND))
                    .given(auctionLifecycleService).closeExpiredAuction(AUCTION_ID);

            mockMvc.perform(post(URL, AUCTION_ID)
                            .header("X-Internal-Token", VALID_TOKEN))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data").value(false));
        }

        @Test
        @DisplayName("그 외 RuntimeException 발생 시 500을 반환한다")
        void unexpectedException_returns500() throws Exception {
            willThrow(new RuntimeException("DB connection failed"))
                    .given(auctionLifecycleService).closeExpiredAuction(AUCTION_ID);

            mockMvc.perform(post(URL, AUCTION_ID)
                            .header("X-Internal-Token", VALID_TOKEN))
                    .andExpect(status().isInternalServerError());
        }

        @Test
        @DisplayName("id가 0 이하이면 400을 반환한다")
        void nonPositiveId_returns400() throws Exception {
            mockMvc.perform(post(URL, 0L)
                            .header("X-Internal-Token", VALID_TOKEN))
                    .andExpect(status().isBadRequest());
        }
    }
}
