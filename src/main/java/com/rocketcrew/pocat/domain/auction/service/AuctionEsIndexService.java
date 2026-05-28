package com.rocketcrew.pocat.domain.auction.service;

import com.rocketcrew.pocat.domain.auction.document.AuctionDocument;
import com.rocketcrew.pocat.domain.auction.entity.Auction;
import com.rocketcrew.pocat.domain.auction.enums.AuctionStatus;
import com.rocketcrew.pocat.domain.auction.repository.AuctionSearchRepository;
import com.rocketcrew.pocat.domain.card.entity.Card;
import com.rocketcrew.pocat.domain.card.repository.CardRepository;
import com.rocketcrew.pocat.domain.user.service.UserQueryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.mapping.IndexCoordinates;
import org.springframework.data.elasticsearch.core.document.Document;
import org.springframework.data.elasticsearch.core.query.UpdateQuery;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuctionEsIndexService {

    private static final String INDEX = "auctions";

    private final AuctionSearchRepository auctionSearchRepository;
    private final CardRepository cardRepository;
    private final UserQueryService userQueryService;
    private final ElasticsearchOperations elasticsearchOperations;

    /** 경매 전체 문서 인덱싱 (ACTIVE 전환 시) */
    public void index(Auction auction) {
        try {
            Card card = cardRepository.findById(auction.getCardId()).orElse(null);
            String sellerNickname = resolveNickname(auction.getSellerId());
            AuctionDocument doc = AuctionDocument.from(auction, card, sellerNickname);
            auctionSearchRepository.save(doc);
            log.debug("[AuctionEs] 인덱싱 완료 auctionId={}", auction.getId());
        } catch (Exception e) {
            log.error("[AuctionEs] 인덱싱 실패 auctionId={}", auction.getId(), e);
        }
    }

    /** 입찰 발생 시 highestPrice만 부분 업데이트 */
    public void updateHighestPrice(Long auctionId, Long highestPrice) {
        try {
            UpdateQuery updateQuery = UpdateQuery.builder(String.valueOf(auctionId))
                    .withDocument(Document.create().append("highestPrice", highestPrice))
                    .build();
            elasticsearchOperations.update(updateQuery, IndexCoordinates.of(INDEX));
            log.debug("[AuctionEs] highestPrice 업데이트 auctionId={} price={}", auctionId, highestPrice);
        } catch (Exception e) {
            log.warn("[AuctionEs] highestPrice 업데이트 실패 auctionId={}", auctionId, e);
        }
    }

    /** 경매 종료(ENDED/NO_BIDDER) 시 status + statusOrder 부분 업데이트 */
    public void updateStatus(Long auctionId, AuctionStatus status) {
        try {
            UpdateQuery updateQuery = UpdateQuery.builder(String.valueOf(auctionId))
                    .withDocument(Document.create()
                            .append("status", status.name())
                            .append("statusOrder", AuctionDocument.toStatusOrder(status)))
                    .build();
            elasticsearchOperations.update(updateQuery, IndexCoordinates.of(INDEX));
            log.debug("[AuctionEs] status 업데이트 auctionId={} status={}", auctionId, status);
        } catch (Exception e) {
            log.warn("[AuctionEs] status 업데이트 실패 auctionId={}", auctionId, e);
        }
    }

    /** 경매 취소 시 인덱스에서 삭제 */
    public void delete(Long auctionId) {
        try {
            auctionSearchRepository.deleteById(String.valueOf(auctionId));
            log.debug("[AuctionEs] 삭제 완료 auctionId={}", auctionId);
        } catch (Exception e) {
            log.warn("[AuctionEs] 삭제 실패 auctionId={}", auctionId, e);
        }
    }

    private String resolveNickname(Long sellerId) {
        try {
            return userQueryService.getUserEntity(sellerId).getNickname();
        } catch (Exception e) {
            log.warn("[AuctionEs] 판매자 닉네임 조회 실패 sellerId={}", sellerId);
            return null;
        }
    }
}
