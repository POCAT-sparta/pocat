package com.rocketcrew.pocat.domain.card.util;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.yaml.snakeyaml.Yaml;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 한글 확장팩명 ↔ DB 영문 확장팩명 변환 사전
 *
 * <ul>
 *   <li>{@link #translate} : 한글 입력 → 영문 DB 값 (필터용)</li>
 *   <li>{@link #getKoreanText} : 영문 DB 값 → 한글 별칭 전체 (공백 구분) (ES 인덱싱용)</li>
 * </ul>
 *
 * 정규화 규칙: 공백·특수문자 제거, 한글(가–힣)·영문·숫자만 유지
 */
@Slf4j
@Component
public class SetNameDictionary {

    private final Map<String, String> koToEn;       // 정규화된 한글 → 영문
    private final Map<String, String> enToKoText;   // 영문 → 한글 별칭 전체(공백 구분)

    public SetNameDictionary() {
        Map<String, String> ko2en = new LinkedHashMap<>();
        Map<String, List<String>> en2koList = new LinkedHashMap<>();

        try (InputStream is = getClass().getResourceAsStream("/set-names.yml")) {
            if (is == null) {
                log.warn("[SetNameDictionary] set-names.yml 리소스를 찾을 수 없음");
            } else {
                Map<Object, Object> root = new Yaml().load(is);
                Object namesObj = root.get("names");
                if (namesObj instanceof Map<?, ?> rawMap) {
                    for (Map.Entry<?, ?> entry : rawMap.entrySet()) {
                        String ko = String.valueOf(entry.getKey());
                        String en = String.valueOf(entry.getValue());
                        ko2en.put(ko, en);
                        en2koList.computeIfAbsent(en, k -> new ArrayList<>()).add(ko);
                    }
                }
                log.info("[SetNameDictionary] {}개 확장팩명 로드 완료", ko2en.size());
            }
        } catch (Exception e) {
            log.warn("[SetNameDictionary] set-names.yml 로드 실패: {}", e.getMessage());
        }

        this.koToEn = Collections.unmodifiableMap(ko2en);

        Map<String, String> en2koText = new LinkedHashMap<>();
        en2koList.forEach((en, koList) -> en2koText.put(en, String.join(" ", koList)));
        this.enToKoText = Collections.unmodifiableMap(en2koText);
    }

    /**
     * 한글(또는 영문) 확장팩명 → DB 영문 확장팩명.
     * 매핑 없으면 원본 반환 (영문 직접 입력 허용).
     */
    public String translate(String input) {
        if (input == null || input.isBlank()) return input;
        return koToEn.getOrDefault(normalize(input), input);
    }

    /**
     * 영문 확장팩명 → YAML에 정의된 모든 한글 별칭을 공백으로 이은 문자열.
     * ES {@code setNameKo} 필드 인덱싱에 사용한다.
     * 예) "Rebel Clash" → "반역크래시"
     */
    public String getKoreanText(String englishSetName) {
        if (englishSetName == null) return null;
        return enToKoText.get(englishSetName);
    }

    /** 공백·특수문자 제거, 한글(가–힣)·영문·숫자만 유지 */
    private static String normalize(String s) {
        return s.replaceAll("[^가-힣a-zA-Z0-9]", "");
    }
}
