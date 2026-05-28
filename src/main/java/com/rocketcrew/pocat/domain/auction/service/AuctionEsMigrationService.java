package com.rocketcrew.pocat.domain.auction.service;

import com.rocketcrew.pocat.domain.auction.document.AuctionDocument;
import com.rocketcrew.pocat.domain.auction.entity.Auction;
import com.rocketcrew.pocat.domain.auction.enums.AuctionStatus;
import com.rocketcrew.pocat.domain.auction.repository.AuctionRepository;
import com.rocketcrew.pocat.domain.auction.repository.AuctionSearchRepository;
import com.rocketcrew.pocat.domain.card.entity.Card;
import com.rocketcrew.pocat.domain.card.repository.CardRepository;
import com.rocketcrew.pocat.domain.user.entity.User;
import com.rocketcrew.pocat.domain.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuctionEsMigrationService {

    private static final int BATCH_SIZE = 200;
    private static final List<AuctionStatus> INDEXABLE_STATUSES = List.of(
            AuctionStatus.ACTIVE,
            AuctionStatus.ENDED,
            AuctionStatus.NO_BIDDER
    );

    private final AuctionRepository auctionRepository;
    private final AuctionSearchRepository auctionSearchRepository;
    private final CardRepository cardRepository;
    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public int migrateAll() {
        int pageNum = 0;
        int totalCount = 0;
        Page<Auction> batch;

        do {
            Pageable pageable = PageRequest.of(pageNum++, BATCH_SIZE);
            batch = auctionRepository.findByStatusIn(INDEXABLE_STATUSES, pageable);

            List<Auction> auctions = batch.getContent();
            if (auctions.isEmpty()) {
                break;
            }

            // 카드 배치 조회 (N+1 방지)
            List<Long> cardIds = auctions.stream().map(Auction::getCardId).distinct().toList();
            Map<Long, Card> cardMap = cardRepository.findAllById(cardIds).stream()
                    .collect(Collectors.toMap(Card::getId, c -> c));

            // 판매자 닉네임 배치 조회 (N+1 방지)
            List<Long> sellerIds = auctions.stream().map(Auction::getSellerId).distinct().toList();
            Map<Long, String> nicknameMap = userRepository.findAllById(sellerIds).stream()
                    .collect(Collectors.toMap(User::getId, User::getNickname));

            List<AuctionDocument> docs = auctions.stream()
                    .map(a -> AuctionDocument.from(
                            a,
                            cardMap.get(a.getCardId()),
                            nicknameMap.get(a.getSellerId())
                    ))
                    .collect(Collectors.toList());

            auctionSearchRepository.saveAll(docs);
            totalCount += docs.size();
            log.info("[AuctionEsMigration] 배치 인덱싱 완료 (누적: {}/{})", totalCount, batch.getTotalElements());

        } while (batch.hasNext());

        log.info("[AuctionEsMigration] 전체 {}개 경매 ES 인덱싱 완료", totalCount);
        return totalCount;
    }
}
