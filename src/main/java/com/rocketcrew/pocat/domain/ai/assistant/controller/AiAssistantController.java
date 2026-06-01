package com.rocketcrew.pocat.domain.ai.assistant.controller;

import com.rocketcrew.pocat.domain.ai.assistant.dto.AiChatRequest;
import com.rocketcrew.pocat.domain.ai.assistant.dto.AiChatResponse;
import com.rocketcrew.pocat.domain.ai.assistant.service.AiAssistantService;
import com.rocketcrew.pocat.global.dto.ApiResponseDto;
import com.rocketcrew.pocat.global.exception.common.ErrorCode;
import com.rocketcrew.pocat.global.exception.common.ServiceException;
import com.rocketcrew.pocat.global.ratelimit.RateLimitProperties;
import com.rocketcrew.pocat.global.ratelimit.RedisRateLimiter;
import com.rocketcrew.pocat.global.security.CustomUserDetails;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/ai/assistant")
@RequiredArgsConstructor
@PreAuthorize("isAuthenticated()")
public class AiAssistantController {

    private final AiAssistantService aiAssistantService;
    private final RedisRateLimiter redisRateLimiter;
    private final RateLimitProperties rateLimitProperties;

    /**
     * AI 어시스턴트와의 채팅.
     * Tool Calling + RAG 컨텍스트 활용.
     *
     * @param request 채팅 요청 (message, sessionId)
     * @param userDetails 인증된 사용자
     * @return 채팅 응답
     */
    @PostMapping("/chat")
    public ResponseEntity<ApiResponseDto<AiChatResponse>> chat(
            @RequestBody @Valid AiChatRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        if (!redisRateLimiter.isAllowed("rate:user:ai:" + userDetails.getUserId(),
                rateLimitProperties.getAiLimit(),
                rateLimitProperties.getAiWindowSeconds())) {
            throw new ServiceException(ErrorCode.RATE_LIMIT_EXCEEDED);
        }
        AiChatResponse response = aiAssistantService.chat(userDetails.getUserId(), request);
        return ResponseEntity.ok(ApiResponseDto.success(HttpStatus.OK, response));
    }
}
