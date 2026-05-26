package com.rocketcrew.pocat.domain.card.document;

import com.rocketcrew.pocat.domain.card.dto.response.CardResponse;
import com.rocketcrew.pocat.domain.card.entity.Card;
import com.rocketcrew.pocat.domain.card.entity.enums.CardCategory;
import com.rocketcrew.pocat.domain.card.entity.enums.CardGrade;
import com.rocketcrew.pocat.domain.card.entity.enums.CardSource;
import com.rocketcrew.pocat.domain.card.entity.enums.CardStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.DateFormat;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;
import org.springframework.data.elasticsearch.annotations.Setting;

import java.time.LocalDateTime;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(indexName = "cards")
@Setting(settingPath = "es-settings/cards-settings.json")
public class CardDocument {

    @Id
    private String id;

    @Field(type = FieldType.Long)
    private Long userId;

    @Field(type = FieldType.Keyword)
    private String tcgdexId;

    @Field(type = FieldType.Text)
    private String name;

    @Field(type = FieldType.Text, analyzer = "ngram_analyzer", searchAnalyzer = "standard")
    private String nameKo;

    @Field(type = FieldType.Keyword)
    private String series;

    @Field(type = FieldType.Text, analyzer = "ngram_analyzer", searchAnalyzer = "standard")
    private String seriesKo;    // 한글 시리즈 별칭 전체 (공백 구분) — 통합 키워드 검색용

    @Field(type = FieldType.Keyword)
    private String setId;

    @Field(type = FieldType.Keyword)
    private String setName;

    @Field(type = FieldType.Text, analyzer = "ngram_analyzer", searchAnalyzer = "standard")
    private String setNameKo;  // 한글 확장팩 별칭 전체 (공백 구분) — 통합 키워드 검색용

    @Field(type = FieldType.Keyword)
    private String cardNumber;

    @Field(type = FieldType.Keyword)
    private String rarity;

    @Field(type = FieldType.Keyword)
    private String category;

    @Field(type = FieldType.Keyword)
    private String grade;

    @Field(type = FieldType.Keyword)
    private String imageUrl;

    @Field(type = FieldType.Keyword)
    private String source;

    @Field(type = FieldType.Keyword)
    private String status;

    @Field(type = FieldType.Date, format = DateFormat.date_hour_minute_second_millis)
    private LocalDateTime createdAt;

    @Field(type = FieldType.Date, format = DateFormat.date_hour_minute_second_millis)
    private LocalDateTime updatedAt;

    public static CardDocument from(Card card) {
        return from(card, null, null, null);
    }

    public static CardDocument from(Card card, String nameKo, String seriesKo, String setNameKo) {
        return CardDocument.builder()
                .id(String.valueOf(card.getId()))
                .userId(card.getUserId())
                .tcgdexId(card.getTcgdexId())
                .name(card.getName())
                .nameKo(nameKo)
                .series(card.getSeries())
                .seriesKo(seriesKo)
                .setId(card.getSetId())
                .setName(card.getSetName())
                .setNameKo(setNameKo)
                .cardNumber(card.getCardNumber())
                .rarity(card.getRarity())
                .category(card.getCategory() != null ? card.getCategory().name() : null)
                .grade(card.getGrade() != null ? card.getGrade().name() : null)
                .imageUrl(card.getImageUrl())
                .source(card.getSource() != null ? card.getSource().name() : null)
                .status(card.getStatus() != null ? card.getStatus().name() : null)
                .createdAt(card.getCreatedAt())
                .updatedAt(card.getUpdatedAt())
                .build();
    }

    public CardResponse toResponse() {
        return new CardResponse(
                Long.parseLong(id),
                userId,
                tcgdexId,
                name,
                series,
                setId,
                setName,
                cardNumber,
                rarity,
                category != null ? CardCategory.valueOf(category) : null,
                grade != null ? CardGrade.valueOf(grade) : null,
                imageUrl,
                source != null ? CardSource.valueOf(source) : null,
                status != null ? CardStatus.valueOf(status) : null,
                createdAt,
                updatedAt
        );
    }
}
