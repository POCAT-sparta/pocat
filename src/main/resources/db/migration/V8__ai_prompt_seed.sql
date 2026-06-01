-- V8: Add UNIQUE INDEX on ai_prompt_template(card_grade) and insert seed prompt data

-- Remove duplicate card_grade rows before adding UNIQUE INDEX, keeping highest id per grade
DELETE FROM ai_prompt_template
WHERE id NOT IN (
    SELECT max_id FROM (
        SELECT MAX(id) AS max_id
        FROM ai_prompt_template
        GROUP BY card_grade
    ) AS keep_ids
);

-- Add UNIQUE INDEX only if it does not already exist (idempotent for Flyway repair scenarios)
SET @index_exists = (
    SELECT COUNT(*) FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'ai_prompt_template'
      AND INDEX_NAME = 'uk_prompt_grade'
);
SET @sql = IF(@index_exists = 0,
    'ALTER TABLE ai_prompt_template ADD UNIQUE INDEX uk_prompt_grade (card_grade)',
    'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- DEFAULT
INSERT INTO ai_prompt_template (card_grade, prompt_text, version, is_active, created_at, updated_at)
SELECT 'DEFAULT',
       '다음 카드 정보를 분석하여 시장 가치와 투자 가능성을 평가하세요.\n\n카드 정보:\n{cardContext}\n\n아래 JSON 형식으로 응답하세요:\n{format}\n\n평가 기준:\n- priceTrend: 가격 추세 (RISING/STABLE/FALLING/VOLATILE 중 하나)\n- fairValueEstimate: 공정 시장 가치 추정 (원화, 숫자만)\n- demandLevel: 수요 수준 (HIGH/MEDIUM/LOW 중 하나)\n- summary: 전반적인 분석 요약 (100자 이내)\n- highlights: 긍정적 요소 목록 (최대 3개)\n- riskFactors: 위험 요소 목록 (최대 3개)\n- keywords: 핵심 키워드 목록 (최대 5개)\n- analysisModel: 사용된 모델명 (응답 그대로 유지)\n- promptTokens: 프롬프트 토큰 수 (응답 그대로 유지)\n- completionTokens: 완성 토큰 수 (응답 그대로 유지)\n- analyzedAt: 분석 시각 (ISO 8601 형식)',
       1, TRUE, NOW(6), NOW(6)
WHERE NOT EXISTS (
    SELECT 1 FROM ai_prompt_template WHERE card_grade = 'DEFAULT'
);

-- PSA_10
INSERT INTO ai_prompt_template (card_grade, prompt_text, version, is_active, created_at, updated_at)
SELECT 'PSA_10',
       'PSA 10 등급 (완벽 상태)에 특화된 분석을 수행하세요.\n\n다음 카드 정보를 분석하여 시장 가치와 투자 가능성을 평가하세요.\n\n카드 정보:\n{cardContext}\n\n아래 JSON 형식으로 응답하세요:\n{format}\n\n평가 기준:\n- priceTrend: 가격 추세 (RISING/STABLE/FALLING/VOLATILE 중 하나)\n- fairValueEstimate: 공정 시장 가치 추정 (원화, 숫자만)\n- demandLevel: 수요 수준 (HIGH/MEDIUM/LOW 중 하나)\n- summary: 전반적인 분석 요약 (100자 이내)\n- highlights: 긍정적 요소 목록 (최대 3개)\n- riskFactors: 위험 요소 목록 (최대 3개)\n- keywords: 핵심 키워드 목록 (최대 5개)\n- analysisModel: 사용된 모델명 (응답 그대로 유지)\n- promptTokens: 프롬프트 토큰 수 (응답 그대로 유지)\n- completionTokens: 완성 토큰 수 (응답 그대로 유지)\n- analyzedAt: 분석 시각 (ISO 8601 형식)',
       1, TRUE, NOW(6), NOW(6)
WHERE NOT EXISTS (
    SELECT 1 FROM ai_prompt_template WHERE card_grade = 'PSA_10'
);

-- PSA_9
INSERT INTO ai_prompt_template (card_grade, prompt_text, version, is_active, created_at, updated_at)
SELECT 'PSA_9',
       'PSA 9 등급 (민트 상태)에 특화된 분석을 수행하세요.\n\n다음 카드 정보를 분석하여 시장 가치와 투자 가능성을 평가하세요.\n\n카드 정보:\n{cardContext}\n\n아래 JSON 형식으로 응답하세요:\n{format}\n\n평가 기준:\n- priceTrend: 가격 추세 (RISING/STABLE/FALLING/VOLATILE 중 하나)\n- fairValueEstimate: 공정 시장 가치 추정 (원화, 숫자만)\n- demandLevel: 수요 수준 (HIGH/MEDIUM/LOW 중 하나)\n- summary: 전반적인 분석 요약 (100자 이내)\n- highlights: 긍정적 요소 목록 (최대 3개)\n- riskFactors: 위험 요소 목록 (최대 3개)\n- keywords: 핵심 키워드 목록 (최대 5개)\n- analysisModel: 사용된 모델명 (응답 그대로 유지)\n- promptTokens: 프롬프트 토큰 수 (응답 그대로 유지)\n- completionTokens: 완성 토큰 수 (응답 그대로 유지)\n- analyzedAt: 분석 시각 (ISO 8601 형식)',
       1, TRUE, NOW(6), NOW(6)
WHERE NOT EXISTS (
    SELECT 1 FROM ai_prompt_template WHERE card_grade = 'PSA_9'
);

-- BGS_10
INSERT INTO ai_prompt_template (card_grade, prompt_text, version, is_active, created_at, updated_at)
SELECT 'BGS_10',
       'BGS Black Label 10 등급에 특화된 분석을 수행하세요.\n\n다음 카드 정보를 분석하여 시장 가치와 투자 가능성을 평가하세요.\n\n카드 정보:\n{cardContext}\n\n아래 JSON 형식으로 응답하세요:\n{format}\n\n평가 기준:\n- priceTrend: 가격 추세 (RISING/STABLE/FALLING/VOLATILE 중 하나)\n- fairValueEstimate: 공정 시장 가치 추정 (원화, 숫자만)\n- demandLevel: 수요 수준 (HIGH/MEDIUM/LOW 중 하나)\n- summary: 전반적인 분석 요약 (100자 이내)\n- highlights: 긍정적 요소 목록 (최대 3개)\n- riskFactors: 위험 요소 목록 (최대 3개)\n- keywords: 핵심 키워드 목록 (최대 5개)\n- analysisModel: 사용된 모델명 (응답 그대로 유지)\n- promptTokens: 프롬프트 토큰 수 (응답 그대로 유지)\n- completionTokens: 완성 토큰 수 (응답 그대로 유지)\n- analyzedAt: 분석 시각 (ISO 8601 형식)',
       1, TRUE, NOW(6), NOW(6)
WHERE NOT EXISTS (
    SELECT 1 FROM ai_prompt_template WHERE card_grade = 'BGS_10'
);
