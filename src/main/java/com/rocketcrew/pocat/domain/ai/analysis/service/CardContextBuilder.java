package com.rocketcrew.pocat.domain.ai.analysis.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rocketcrew.pocat.domain.ai.analysis.config.AnalysisExchangeRateProperties;
import com.rocketcrew.pocat.domain.card.entity.Card;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

/**
 * 카드 AI 분석을 위한 컨텍스트(프롬프트의 {cardContext} 값)를 구성한다.
 *
 * <p>내부 DB 정보에 더해, 가능하면 TCGdex 실시간 데이터(시세 포함)를 검증용으로 덧붙이고,
 * 외화 시세를 원화로 환산할 수 있도록 환율 정보를 함께 주입한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CardContextBuilder {

    private final ObjectMapper objectMapper;
    private final AnalysisExchangeRateProperties exchangeRateProperties;

    private static final String TCGDEX_CARD_URL = "https://api.tcgdex.net/v2/en/cards/";

    /**
     * TCGdex 실시간 조회용 RestTemplate.
     * 사용자 요청 경로(분석 캐시 미스)에서 호출되므로 타임아웃을 짧게 둔다.
     */
    private final RestTemplate tcgdexRestTemplate = createTcgdexRestTemplate();

    private static RestTemplate createTcgdexRestTemplate() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(3_000);
        factory.setReadTimeout(5_000);
        return new RestTemplate(factory);
    }

    /**
     * 카드 정보를 분석 대상 컨텍스트로 구성.
     * 내부 DB 정보 + (가능하면) TCGdex 실측 데이터 + (TCGdex 시세가 있을 때) 환율 정보 순으로 조립한다.
     */
    public String build(Card card) {
        String internalContext = buildInternalContext(card);

        String tcgdexContext = buildTcgdexContext(card.getTcgdexId());
        if (tcgdexContext.isEmpty()) {
            return internalContext;
        }
        // TCGdex 시세는 EUR/USD이므로 원화 환산용 환율을 함께 제공한다.
        return internalContext + "\n\n" + tcgdexContext + "\n\n" + buildExchangeRateContext();
    }

    private String buildInternalContext(Card card) {
        return String.format(
                "카드 이름: %s\n등급: %s\n시리즈: %s\n세트: %s\nURL: %s\n레어도: %s",
                card.getName(),
                card.getGrade().toString(),
                card.getSeries() != null ? card.getSeries().getName() : "N/A",
                card.getPokemonSet() != null ? card.getPokemonSet().getName() : "N/A",
                card.getImageUrl() != null ? card.getImageUrl() : "N/A",
                card.getRarity()
        );
    }

    /**
     * 원화 환산을 위한 환율 정보 블록. LLM이 EUR/USD 시세를 fairValueEstimate(원화)로 환산할 때 사용한다.
     */
    private String buildExchangeRateContext() {
        return String.format(
                "[환율 정보] (외화 시세를 fairValueEstimate(원화) 환산 시 사용)\n1 USD = %.0f원\n1 EUR = %.0f원",
                exchangeRateProperties.getUsdToKrw(),
                exchangeRateProperties.getEurToKrw()
        );
    }

    /**
     * TCGdex API에서 카드의 실시간 데이터를 조회하여 분석에 유의미한 필드만 추려 컨텍스트 블록으로 만든다.
     * 시세(cardmarket/tcgplayer)를 포함해 LLM이 내부 정보를 교차 검증할 수 있도록 한다.
     *
     * <p>외부 의존성이므로 fail-open: tcgdexId가 없거나 조회/파싱에 실패하면 빈 문자열을 반환하여
     * 내부 정보만으로 분석이 계속 진행되도록 한다(분석 자체를 막지 않는다).
     */
    private String buildTcgdexContext(String tcgdexId) {
        if (tcgdexId == null || tcgdexId.isBlank()) {
            log.debug("tcgdexId 없음, TCGdex 검증 데이터 생략");
            return "";
        }
        try {
            String cardJson = tcgdexRestTemplate.getForObject(TCGDEX_CARD_URL + tcgdexId, String.class);
            if (cardJson == null || cardJson.isBlank()) {
                return "";
            }
            JsonNode root = objectMapper.readTree(cardJson);
            return formatTcgdexContext(root);
        } catch (Exception e) {
            log.warn("TCGdex 검증 데이터 조회 실패 tcgdexId={}: {} — 내부 정보만으로 분석 진행", tcgdexId, e.getMessage());
            return "";
        }
    }

    /**
     * TCGdex 응답 JSON에서 분석에 유의미한 필드만 추출해 한국어 라벨 블록으로 포맷한다.
     * image/illustrator/productId 등 분석과 무관한 노이즈 필드는 토큰 절감을 위해 제외한다.
     */
    private String formatTcgdexContext(JsonNode root) {
        StringBuilder sb = new StringBuilder("[TCGdex 실측 데이터]");

        appendLine(sb, "공식명", text(root, "name"));
        appendLine(sb, "레어도", text(root, "rarity"));

        // 카드 스펙 (HP / 타입 / 진화 단계 / 진화 전)
        String hp = text(root, "hp");
        String types = joinArray(root.path("types"));
        String stage = text(root, "stage");
        String evolveFrom = text(root, "evolveFrom");
        StringBuilder spec = new StringBuilder();
        if (hp != null) spec.append("HP ").append(hp);
        if (types != null) appendPart(spec, "타입 " + types);
        if (stage != null) appendPart(spec, "단계 " + stage);
        if (evolveFrom != null) appendPart(spec, "진화 전 " + evolveFrom);
        if (!spec.isEmpty()) sb.append("\n").append(spec);

        // 기술 / 약점 / 후퇴 비용
        String attacks = formatAttacks(root.path("attacks"));
        if (attacks != null) sb.append("\n기술: ").append(attacks);
        String weaknesses = formatWeaknesses(root.path("weaknesses"));
        if (weaknesses != null) sb.append("\n약점: ").append(weaknesses);
        appendLine(sb, "후퇴 비용", text(root, "retreat"));

        // 세트 / 규격
        JsonNode set = root.path("set");
        if (set.isObject()) {
            String setName = text(set, "name");
            String total = text(set.path("cardCount"), "total");
            if (setName != null) {
                sb.append("\n세트: ").append(setName);
                if (total != null) sb.append(" (").append(total).append("장)");
            }
        }
        appendLine(sb, "레귤레이션 마크", text(root, "regulationMark"));
        JsonNode legal = root.path("legal");
        if (legal.isObject()) {
            sb.append("\n포맷: 정규 ").append(legal.path("standard").asBoolean(false) ? "가능" : "불가")
              .append(", 익스팬디드 ").append(legal.path("expanded").asBoolean(false) ? "가능" : "불가");
        }

        // 시세 (검증의 핵심 신호)
        String pricing = formatPricing(root.path("pricing"));
        if (pricing != null) sb.append("\n").append(pricing);

        return sb.toString();
    }

    /** cardmarket / tcgplayer 시세를 통화 단위와 함께 추출. */
    private String formatPricing(JsonNode pricing) {
        if (!pricing.isObject()) return null;
        StringBuilder sb = new StringBuilder();

        JsonNode cm = pricing.path("cardmarket");
        if (cm.isObject()) {
            String unit = cm.path("unit").asText("");
            StringBuilder line = new StringBuilder("시세(cardmarket");
            if (!unit.isBlank()) line.append(",").append(unit);
            line.append("): ");
            StringBuilder parts = new StringBuilder();
            appendPart(parts, num(cm, "avg", "avg "));
            appendPart(parts, num(cm, "avg30", "30일평균 "));
            appendPart(parts, num(cm, "trend", "trend "));
            appendPart(parts, num(cm, "low", "low "));
            if (!parts.isEmpty()) sb.append(line).append(parts);
        }

        JsonNode tp = pricing.path("tcgplayer");
        if (tp.isObject()) {
            String unit = tp.path("unit").asText("");
            String normal = formatTcgplayerVariant(tp.path("normal"), unit, "일반");
            String reverse = formatTcgplayerVariant(tp.path("reverse-holofoil"), unit, "리버스홀로");
            if (normal != null) appendLineRaw(sb, normal);
            if (reverse != null) appendLineRaw(sb, reverse);
        }

        return !sb.isEmpty() ? sb.toString() : null;
    }

    private String formatTcgplayerVariant(JsonNode variant, String unit, String label) {
        if (!variant.isObject()) return null;
        StringBuilder parts = new StringBuilder();
        appendPart(parts, num(variant, "marketPrice", "market "));
        appendPart(parts, num(variant, "lowPrice", "low "));
        appendPart(parts, num(variant, "highPrice", "high "));
        if (parts.isEmpty()) return null;
        StringBuilder line = new StringBuilder("시세(tcgplayer-").append(label);
        if (!unit.isBlank()) line.append(",").append(unit);
        line.append("): ").append(parts);
        return line.toString();
    }

    /** attacks 배열을 "이름(데미지)" 형태로 간결하게 포맷. */
    private String formatAttacks(JsonNode attacks) {
        if (!attacks.isArray() || attacks.isEmpty()) return null;
        StringBuilder sb = new StringBuilder();
        for (JsonNode atk : attacks) {
            String name = text(atk, "name");
            if (name == null) continue;
            if (!sb.isEmpty()) sb.append(", ");
            sb.append(name);
            String damage = text(atk, "damage");
            if (damage != null) sb.append("(").append(damage).append(")");
        }
        return !sb.isEmpty() ? sb.toString() : null;
    }

    /** weaknesses 배열을 "타입 배수" 형태로 포맷. */
    private String formatWeaknesses(JsonNode weaknesses) {
        if (!weaknesses.isArray() || weaknesses.isEmpty()) return null;
        StringBuilder sb = new StringBuilder();
        for (JsonNode w : weaknesses) {
            String type = text(w, "type");
            if (type == null) continue;
            if (!sb.isEmpty()) sb.append(", ");
            sb.append(type);
            String value = text(w, "value");
            if (value != null) sb.append(" ").append(value);
        }
        return !sb.isEmpty() ? sb.toString() : null;
    }

    /** 노드에서 텍스트 값을 읽되, 없거나 null이면 null 반환. */
    private String text(JsonNode node, String field) {
        JsonNode v = node.path(field);
        if (v.isMissingNode() || v.isNull()) return null;
        String s = v.asText();
        return (s == null || s.isBlank()) ? null : s;
    }

    /** 숫자 필드를 "라벨 값" 형태로 반환하되, 없으면 null. */
    private String num(JsonNode node, String field, String label) {
        JsonNode v = node.path(field);
        if (v.isMissingNode() || v.isNull() || !v.isNumber()) return null;
        return label + v.asText();
    }

    private void appendLine(StringBuilder sb, String label, String value) {
        if (value != null) sb.append("\n").append(label).append(": ").append(value);
    }

    private void appendLineRaw(StringBuilder sb, String line) {
        if (!sb.isEmpty()) sb.append("\n");
        sb.append(line);
    }

    private void appendPart(StringBuilder sb, String part) {
        if (part == null) return;
        if (!sb.isEmpty()) sb.append(", ");
        sb.append(part);
    }

    private String joinArray(JsonNode array) {
        if (!array.isArray() || array.isEmpty()) return null;
        StringBuilder sb = new StringBuilder();
        for (JsonNode n : array) {
            if (!sb.isEmpty()) sb.append(", ");
            sb.append(n.asText());
        }
        return !sb.isEmpty() ? sb.toString() : null;
    }
}
