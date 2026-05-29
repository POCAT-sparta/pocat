package com.rocketcrew.pocat.domain.auction.document;

import com.rocketcrew.pocat.domain.auction.dto.response.SearchAuctionResponse;
import com.rocketcrew.pocat.domain.auction.entity.Auction;
import com.rocketcrew.pocat.domain.auction.enums.AuctionStatus;
import com.rocketcrew.pocat.domain.card.entity.Card;
import com.rocketcrew.pocat.domain.card.entity.enums.CardGrade;
import com.rocketcrew.pocat.domain.pokemon.entity.Pokemon;
import com.rocketcrew.pocat.domain.series.entity.Series;
import com.rocketcrew.pocat.domain.set.entity.PokemonSet;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.DateFormat;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;
import org.springframework.data.elasticsearch.annotations.InnerField;
import org.springframework.data.elasticsearch.annotations.MultiField;
import org.springframework.data.elasticsearch.annotations.Setting;

import java.time.LocalDateTime;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(indexName = "auctions")
@Setting(settingPath = "es-settings/auctions-settings.json")
public class AuctionDocument {

    @Id
    private String id;

    // ── 경매 기본 정보 ──────────────────────────────────────────────
    @Field(type = FieldType.Long)    private Long   sellerId;
    @Field(type = FieldType.Keyword) private String sellerNickname;
    @Field(type = FieldType.Text)    private String title;
    @Field(type = FieldType.Text)    private String description;
    @Field(type = FieldType.Keyword) private String status;
    @Field(type = FieldType.Integer) private int    statusOrder;   // ACTIVE=0, ENDED=1, NO_BIDDER=2
    @Field(type = FieldType.Long)    private Long   startingPrice;
    @Field(type = FieldType.Long)    private Long   buyoutPrice;
    @Field(type = FieldType.Long)    private Long   highestPrice;

    @Field(type = FieldType.Date, format = DateFormat.date_hour_minute_second_millis)
    private LocalDateTime startedAt;
    @Field(type = FieldType.Date, format = DateFormat.date_hour_minute_second_millis)
    private LocalDateTime endedAt;
    @Field(type = FieldType.Date, format = DateFormat.date_hour_minute_second_millis)
    private LocalDateTime createdAt;

    // ── 카드 비정규화 (CardDocument와 동일 패턴) ───────────────────
    @Field(type = FieldType.Long)    private Long   cardId;
    @Field(type = FieldType.Text)    private String cardName;

    @Field(type = FieldType.Text, analyzer = "ngram_analyzer", searchAnalyzer = "standard")
    private String cardNameKo;

    @MultiField(
        mainField  = @Field(type = FieldType.Keyword),
        otherFields = @InnerField(suffix = "text", type = FieldType.Text)
    )
    private String series;

    @Field(type = FieldType.Text, analyzer = "ngram_analyzer", searchAnalyzer = "standard")
    private String seriesKo;

    @Field(type = FieldType.Keyword) private String setId;

    @MultiField(
        mainField  = @Field(type = FieldType.Keyword),
        otherFields = @InnerField(suffix = "text", type = FieldType.Text)
    )
    private String setName;

    @Field(type = FieldType.Text, analyzer = "ngram_analyzer", searchAnalyzer = "standard")
    private String setNameKo;

    @Field(type = FieldType.Keyword) private String grade;
    @Field(type = FieldType.Keyword) private String category;
    @Field(type = FieldType.Keyword) private String imageUrl;

    // ── 팩토리 ─────────────────────────────────────────────────────

    public static AuctionDocument from(Auction auction, Card card, String sellerNickname) {
        Series    s  = card != null ? card.getSeries()    : null;
        PokemonSet ps = card != null ? card.getPokemonSet() : null;
        Pokemon   p  = card != null ? card.getPokemon()   : null;

        return AuctionDocument.builder()
                .id(String.valueOf(auction.getId()))
                .sellerId(auction.getSellerId())
                .sellerNickname(sellerNickname)
                .title(auction.getTitle())
                .description(auction.getDescription())
                .status(auction.getStatus() != null ? auction.getStatus().name() : null)
                .statusOrder(toStatusOrder(auction.getStatus()))
                .startingPrice(auction.getStartingPrice())
                .buyoutPrice(auction.getBuyoutPrice())
                .highestPrice(auction.getHighestPrice())
                .startedAt(auction.getStartedAt())
                .endedAt(auction.getEndedAt())
                .createdAt(auction.getCreatedAt())
                .cardId(card != null ? card.getId() : null)
                .cardName(card != null ? card.getName() : null)
                .cardNameKo(p  != null ? p.getNameKo()   : null)
                .series(s  != null ? s.getName()  : null)
                .seriesKo(s  != null ? s.getNameKo() : null)
                .setId(ps != null ? ps.getSetId() : null)
                .setName(ps != null ? ps.getName()  : null)
                .setNameKo(ps != null ? ps.getNameKo() : null)
                .grade(card != null && card.getGrade()    != null ? card.getGrade().name()    : null)
                .category(card != null && card.getCategory() != null ? card.getCategory().name() : null)
                .imageUrl(card != null ? card.getImageUrl() : null)
                .build();
    }

    /** AuctionStatus → 정렬 우선순위 숫자 (낮을수록 위) */
    public static int toStatusOrder(AuctionStatus status) {
        if (status == null) return 3;
        return switch (status) {
            case ACTIVE    -> 0;
            case ENDED     -> 1;
            case NO_BIDDER -> 2;
            default        -> 3;
        };
    }

    public SearchAuctionResponse toResponse() {
        return new SearchAuctionResponse(
                Long.parseLong(id),
                sellerId,
                sellerNickname,
                title,
                cardId,
                cardName,
                grade != null ? CardGrade.valueOf(grade) : null,
                imageUrl,
                startingPrice,
                highestPrice,
                buyoutPrice,
                status != null ? AuctionStatus.valueOf(status) : null,
                startedAt,
                endedAt,
                createdAt
        );
    }
}
