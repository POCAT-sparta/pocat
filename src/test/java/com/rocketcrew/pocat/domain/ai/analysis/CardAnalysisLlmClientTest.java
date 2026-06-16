package com.rocketcrew.pocat.domain.ai.analysis;

import com.rocketcrew.pocat.domain.ai.analysis.dto.CardAnalysisResult;
import com.rocketcrew.pocat.domain.ai.analysis.service.CardAnalysisLlmClient;
import com.rocketcrew.pocat.global.exception.common.ServiceException;
import com.rocketcrew.pocat.global.metrics.AiUsageMetrics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.prompt.Prompt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("CardAnalysisLlmClient — 환각 방어 Layer1(재시도)")
class CardAnalysisLlmClientTest {

    @InjectMocks
    private CardAnalysisLlmClient cardAnalysisLlmClient;

    @Mock
    private ChatClient chatClient;

    @Mock
    private AiUsageMetrics aiUsageMetrics;

    @Mock
    private ChatClient.ChatClientRequestSpec requestSpec;

    @Mock
    private ChatClient.CallResponseSpec callResponseSpec;

    private static final String PROMPT = "카드 분석: {cardContext}\n{format}";

    @BeforeEach
    void setUp() {
        given(chatClient.prompt(any(Prompt.class))).willReturn(requestSpec);
        given(requestSpec.call()).willReturn(callResponseSpec);
    }

    @Test
    @DisplayName("파싱 실패 1회 후 재시도하여 2차 성공 → LLM 2회 호출")
    void parseFailOnce_retriesAndSucceeds() {
        // given — 1차: 파싱 불가, 2차: 유효 JSON
        String validJson = "{\"priceTrend\":\"RISING\",\"fairValueEstimate\":150000,\"demandLevel\":\"HIGH\","
                + "\"summary\":\"재시도 성공\",\"highlights\":[],\"riskFactors\":[],\"keywords\":[],"
                + "\"analysisModel\":\"gemini-1.5-flash\",\"promptTokens\":100,\"completionTokens\":200,"
                + "\"analyzedAt\":\"2026-05-26T00:00:00\"}";
        given(callResponseSpec.content())
                .willReturn("THIS_IS_NOT_JSON_WILL_FAIL_PARSING")
                .willReturn(validJson);

        // when
        CardAnalysisResult result = cardAnalysisLlmClient.analyze("카드 컨텍스트", PROMPT);

        // then
        assertThat(result).isNotNull();
        assertThat(result.priceTrend()).isEqualTo("RISING");
        verify(chatClient, times(2)).prompt(any(Prompt.class));
    }

    @Test
    @DisplayName("파싱 2회 연속 실패 시 ServiceException + LLM 2회 호출 검증")
    void parseFailTwice_throwsAfterRetry() {
        // given — 1차, 2차 모두 파싱 불가
        given(callResponseSpec.content())
                .willReturn("INVALID_JSON_FIRST")
                .willReturn("INVALID_JSON_SECOND");

        // when / then
        assertThatThrownBy(() -> cardAnalysisLlmClient.analyze("카드 컨텍스트", PROMPT))
                .isInstanceOf(ServiceException.class);
        verify(chatClient, times(2)).prompt(any(Prompt.class));
        verify(aiUsageMetrics).recordError(Mockito.eq("PARSE_FAILED_AFTER_RETRY"), Mockito.anyString());
    }

    @Test
    @DisplayName("LLM 호출 자체 실패 시 ServiceException + LLM_CALL_FAILED 메트릭")
    void llmCallFails_recordsError() {
        // given
        given(callResponseSpec.content()).willThrow(new RuntimeException("연결 실패"));

        // when / then
        assertThatThrownBy(() -> cardAnalysisLlmClient.analyze("카드 컨텍스트", PROMPT))
                .isInstanceOf(ServiceException.class);
        verify(aiUsageMetrics).recordError(Mockito.eq("LLM_CALL_FAILED"), Mockito.anyString());
    }
}
