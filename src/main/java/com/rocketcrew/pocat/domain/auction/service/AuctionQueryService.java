package com.rocketcrew.pocat.domain.auction.service;

import co.elastic.clients.elasticsearch._types.FieldValue;
import co.elastic.clients.elasticsearch._types.SortOptions;
import co.elastic.clients.elasticsearch._types.SortOrder;
import co.elastic.clients.elasticsearch._types.query_dsl.BoolQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.MultiMatchQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.Operator;
import co.elastic.clients.elasticsearch._types.query_dsl.TermQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.TermsQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.TermsQueryField;
import co.elastic.clients.elasticsearch._types.query_dsl.TextQueryType;
import com.rocketcrew.pocat.domain.auction.document.AuctionDocument;
import com.rocketcrew.pocat.domain.auction.dto.request.AuctionSearchCondition;
import com.rocketcrew.pocat.domain.auction.dto.response.AdminAuctionResponse;
import com.rocketcrew.pocat.domain.auction.dto.response.AuctionResponse;
import com.rocketcrew.pocat.domain.auction.dto.response.SearchAuctionResponse;
import com.rocketcrew.pocat.domain.auction.entity.Auction;
import com.rocketcrew.pocat.domain.auction.enums.AuctionStatus;
import com.rocketcrew.pocat.domain.auction.repository.AuctionRepository;
import com.rocketcrew.pocat.domain.card.entity.Card;
import com.rocketcrew.pocat.domain.card.entity.enums.CardCategory;
import com.rocketcrew.pocat.domain.card.entity.enums.CardGrade;
import com.rocketcrew.pocat.domain.card.service.CardQueryService;
import com.rocketcrew.pocat.domain.like.service.LikeQueryService;
import com.rocketcrew.pocat.domain.series.service.SeriesQueryService;
import com.rocketcrew.pocat.domain.set.service.PokemonSetQueryService;
import com.rocketcrew.pocat.domain.user.entity.User;
import com.rocketcrew.pocat.domain.user.service.UserQueryService;
import com.rocketcrew.pocat.global.exception.common.ErrorCode;
import com.rocketcrew.pocat.global.exception.domain.AuctionException;
import com.rocketcrew.pocat.global.exception.domain.CardException;
import com.rocketcrew.pocat.global.exception.domain.UserException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.elasticsearch.client.elc.NativeQuery;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.SearchHit;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AuctionQueryService {

    private static final Set<AuctionStatus> PUBLIC_DETAIL_STATUSES = Set.of(
            AuctionStatus.ACTIVE,
            AuctionStatus.ENDED,
            AuctionStatus.NO_BIDDER
    );
    private static final Set<AuctionStatus> PUBLIC_LIST_STATUSES = Set.copyOf(List.of(
            AuctionStatus.ACTIVE,
            AuctionStatus.ENDED,
            AuctionStatus.NO_BIDDER
    ));

    private final AuctionRepository auctionRepository;
    private final CardQueryService cardQueryService;
    private final UserQueryService userQueryService;
    private final LikeQueryService likeQueryService;
    private final ElasticsearchOperations elasticsearchOperations;
    private final SeriesQueryService seriesQueryService;
    private final PokemonSetQueryService pokemonSetQueryService;

    public Page<SearchAuctionResponse> getAuctions(
            String keyword,
            String series,
            String setName,
            CardGrade grade,
            CardCategory category,
            AuctionStatus status,
            Pageable pageable
    ) {
        validatePublicListStatus(status);

        BoolQuery.Builder bool = new BoolQuery.Builder();

        // 상태 필터: 지정된 상태 or 기본 공개 상태(ACTIVE/ENDED/NO_BIDDER)
        if (status != null) {
            bool.filter(TermQuery.of(t -> t.field("status").value(status.name()))._toQuery());
        } else {
            bool.filter(TermsQuery.of(t -> t
                    .field("status")
                    .terms(TermsQueryField.of(tf -> tf.value(List.of(
                            FieldValue.of("ACTIVE"),
                            FieldValue.of("ENDED"),
                            FieldValue.of("NO_BIDDER")
                    ))))
            )._toQuery());
        }

        // 키워드: title, cardName, cardNameKo, seriesKo, setNameKo 교차 필드 검색
        if (StringUtils.hasText(keyword)) {
            bool.must(MultiMatchQuery.of(m -> m
                    .fields("title", "cardName", "cardNameKo", "seriesKo", "setNameKo")
                    .query(keyword)
                    .type(TextQueryType.CrossFields)
                    .operator(Operator.And))._toQuery());
        }
        if (StringUtils.hasText(series)) {
            String seriesEn = seriesQueryService.translate(series);
            bool.filter(TermQuery.of(t -> t.field("series").value(seriesEn))._toQuery());
        }
        if (StringUtils.hasText(setName)) {
            String setNameEn = pokemonSetQueryService.translate(setName);
            bool.filter(TermQuery.of(t -> t.field("setName").value(setNameEn))._toQuery());
        }
        if (grade != null) {
            bool.filter(TermQuery.of(t -> t.field("grade").value(grade.name()))._toQuery());
        }
        if (category != null) {
            bool.filter(TermQuery.of(t -> t.field("category").value(category.name()))._toQuery());
        }

        // 정렬: 키워드 있으면 관련도 우선, 그 다음 상태순 → 시작일 역순 → 생성일 역순
        List<SortOptions> sorts = new ArrayList<>();
        if (StringUtils.hasText(keyword)) {
            sorts.add(SortOptions.of(s -> s.score(sc -> sc.order(SortOrder.Desc))));
        }
        sorts.add(SortOptions.of(s -> s.field(f -> f.field("statusOrder").order(SortOrder.Asc))));
        sorts.add(SortOptions.of(s -> s.field(f -> f.field("startedAt").order(SortOrder.Desc))));
        sorts.add(SortOptions.of(s -> s.field(f -> f.field("createdAt").order(SortOrder.Desc))));

        NativeQuery query = NativeQuery.builder()
                .withQuery(bool.build()._toQuery())
                .withPageable(PageRequest.of(pageable.getPageNumber(), pageable.getPageSize()))
                .withSort(sorts)
                .build();

        SearchHits<AuctionDocument> hits = elasticsearchOperations.search(query, AuctionDocument.class);
        List<SearchAuctionResponse> content = hits.stream()
                .map(SearchHit::getContent)
                .map(AuctionDocument::toResponse)
                .toList();

        return new PageImpl<>(content, pageable, hits.getTotalHits());
    }

    public Page<AdminAuctionResponse> getAdminAuctions(
            String keyword,
            String series,
            String setName,
            CardGrade grade,
            CardCategory category,
            AuctionStatus status,
            Pageable pageable
    ) {
        AuctionSearchCondition condition = new AuctionSearchCondition(
                keyword, series, setName, grade, category, status);
        return auctionRepository.searchAdminAuctions(condition, pageable);
    }

    public Page<SearchAuctionResponse> getMyAuctions(Long sellerId, AuctionStatus status, Pageable pageable) {
        return auctionRepository.searchMyAuctions(sellerId, status, pageable);
    }

    public AuctionResponse getAuction(Long id, Long userId) {
        Auction auction = findAuctionEntityOrThrow(id);
        if (!canViewAuctionDetail(auction, userId)) {
            throw new AuctionException(ErrorCode.AUCTION_NOT_FOUND);
        }
        Card card = getAuctionCard(auction.getCardId());
        User seller = getAuctionSeller(auction.getSellerId());
        User highestBidder = auction.getHighestBidderId() == null
                ? null
                : getAuctionHighestBidder(auction.getHighestBidderId());
        long likeCount = likeQueryService.countByAuctionId(auction.getId());
        boolean isLiked = userId != null && likeQueryService.existsByUserIdAndAuctionId(userId, auction.getId());

        return AuctionResponse.of(auction, seller, card, highestBidder, likeCount, isLiked);
    }

    private boolean canViewAuctionDetail(Auction auction, Long userId) {
        return PUBLIC_DETAIL_STATUSES.contains(auction.getStatus())
                || auction.getSellerId().equals(userId);
    }

    private void validatePublicListStatus(AuctionStatus status) {
        if (status != null && !PUBLIC_LIST_STATUSES.contains(status)) {
            throw new AuctionException(ErrorCode.INVALID_INPUT);
        }
    }

    public Auction findAuctionEntityOrThrow(Long id) {
        return auctionRepository.findById(id)
                .orElseThrow(() -> new AuctionException(ErrorCode.AUCTION_NOT_FOUND));
    }

    private Card getAuctionCard(Long cardId) {
        try {
            return cardQueryService.getCardEntity(cardId);
        } catch (CardException e) {
            throw new AuctionException(ErrorCode.AUCTION_CARD_NOT_FOUND);
        }
    }

    private User getAuctionSeller(Long sellerId) {
        try {
            return userQueryService.getUserEntity(sellerId);
        } catch (UserException e) {
            throw new AuctionException(ErrorCode.AUCTION_SELLER_NOT_FOUND);
        }
    }

    private User getAuctionHighestBidder(Long highestBidderId) {
        try {
            return userQueryService.getUserEntity(highestBidderId);
        } catch (UserException e) {
            throw new AuctionException(ErrorCode.AUCTION_HIGHEST_BIDDER_NOT_FOUND);
        }
    }
}
