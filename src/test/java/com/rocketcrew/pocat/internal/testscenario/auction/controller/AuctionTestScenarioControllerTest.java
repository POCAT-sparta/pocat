package com.rocketcrew.pocat.internal.testscenario.auction.controller;

import com.rocketcrew.pocat.domain.auction.enums.AuctionStatus;
import com.rocketcrew.pocat.global.config.SecurityConfig;
import com.rocketcrew.pocat.global.security.JwtUtil;
import com.rocketcrew.pocat.internal.testscenario.auction.dto.AuctionExpirationInjectionResponse;
import com.rocketcrew.pocat.internal.testscenario.auction.service.AuctionTestScenarioService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = AuctionTestScenarioController.class)
@Import(SecurityConfig.class)
@TestPropertySource(properties = {"pocat.internal.token=test-token", "cors.allowed-origins=http://localhost:3000"})
@DisplayName("AuctionTestScenarioController")
class AuctionTestScenarioControllerTest {

    private static final Long AUCTION_ID = 1L;
    private static final String VALID_TOKEN = "test-token";
    private static final String INVALID_TOKEN = "wrong-token";

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AuctionTestScenarioService auctionTestScenarioService;

    @MockBean
    private JwtUtil jwtUtil;

    @MockBean
    private StringRedisTemplate stringRedisTemplate;

    @Test
    @Tag("integration")
    @DisplayName("/internal/test/** 경로도 X-Internal-Token 없으면 401")
    void internalTestPath_withoutToken_returns401() throws Exception {
        mockMvc.perform(post("/internal/test/auctions/{auctionId}/expire-now", AUCTION_ID))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @Tag("integration")
    @DisplayName("/internal/test/** 경로도 X-Internal-Token 값이 틀리면 401")
    void internalTestPath_withInvalidToken_returns401() throws Exception {
        mockMvc.perform(post("/internal/test/auctions/{auctionId}/expire-now", AUCTION_ID)
                        .header("X-Internal-Token", INVALID_TOKEN))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("/internal/test/** 경로는 올바른 X-Internal-Token으로 호출 가능")
    void internalTestPath_withValidToken_returns200() throws Exception {
        LocalDateTime beforeEndedAt = LocalDateTime.of(2026, 1, 1, 0, 0);
        LocalDateTime afterEndedAt = LocalDateTime.of(2026, 1, 1, 0, 10);
        given(auctionTestScenarioService.makeExpired(AUCTION_ID))
                .willReturn(new AuctionExpirationInjectionResponse(
                        AUCTION_ID,
                        AuctionStatus.ACTIVE,
                        beforeEndedAt,
                        afterEndedAt,
                        true,
                        "POST /internal/auctions/1/close-expired"
                ));

        mockMvc.perform(post("/internal/test/auctions/{auctionId}/expire-now", AUCTION_ID)
                        .header("X-Internal-Token", VALID_TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.auctionId").value(AUCTION_ID));
    }
}
