package com.rocketcrew.pocat.domain.ai.analysis.service;

import com.rocketcrew.pocat.domain.ai.analysis.dto.CardAnalysisResult;
import com.rocketcrew.pocat.global.exception.common.ErrorCode;
import com.rocketcrew.pocat.global.exception.common.ServiceException;
import com.rocketcrew.pocat.global.metrics.AiUsageMetrics;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 카드 분석용 LLM 호출 및 응답 파싱을 담당한다.
 * 파싱 실패 시 1회 재시도(환각 방어 Layer1)하며, 실패 시 메트릭을 기록한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CardAnalysisLlmClient {

    private final ChatClient chatClient;
    private final AiUsageMetrics aiUsageMetrics;

    private static final String FALLBACK_MODEL = "gemini-1.5-flash";

    /**
     * 프롬프트 템플릿과 카드 컨텍스트로 LLM을 호출하고 결과를 파싱한다.
     *
     * @param cardContext    프롬프트의 {cardContext} 값
     * @param promptTemplate 등급별 프롬프트 템플릿
     * @return 파싱된 분석 결과
     * @throws ServiceException 호출 실패 또는 재시도 후에도 파싱 실패 시
     */
    public CardAnalysisResult analyze(String cardContext, String promptTemplate) {
        try {
            BeanOutputConverter<CardAnalysisResult> outputConverter =
                    new BeanOutputConverter<>(CardAnalysisResult.class);

            PromptTemplate template = new PromptTemplate(promptTemplate);
            Prompt prompt = template.create(Map.of(
                    "cardContext", cardContext,
                    "format", outputConverter.getFormat()
            ));

            String response = chatClient.prompt(prompt).call().content();

            log.debug("LLM response received for card analysis");
            try {
                return outputConverter.convert(response);
            } catch (Exception firstEx) {
                log.warn("BeanOutputConverter parsing failed on first attempt, retrying: {}", firstEx.getMessage());
                // 1회 재시도
                String retryResponse = chatClient.prompt(prompt).call().content();
                try {
                    return outputConverter.convert(retryResponse);
                } catch (Exception retryEx) {
                    log.error("BeanOutputConverter parsing failed after retry: {}", retryEx.getMessage(), retryEx);
                    aiUsageMetrics.recordError("PARSE_FAILED_AFTER_RETRY", FALLBACK_MODEL);
                    throw new ServiceException(ErrorCode.INTERNAL_SERVER_ERROR, retryEx);
                }
            }
        } catch (ServiceException e) {
            throw e;
        } catch (Exception e) {
            log.error("LLM call failed: {}", e.getMessage(), e);
            aiUsageMetrics.recordError("LLM_CALL_FAILED", FALLBACK_MODEL);
            throw new ServiceException(ErrorCode.INTERNAL_SERVER_ERROR, e);
        }
    }
}
