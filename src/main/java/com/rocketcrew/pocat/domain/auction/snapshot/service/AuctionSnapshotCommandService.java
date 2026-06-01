package com.rocketcrew.pocat.domain.auction.snapshot.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rocketcrew.pocat.domain.auction.entity.Auction;
import com.rocketcrew.pocat.domain.auction.repository.AuctionRepository;
import com.rocketcrew.pocat.domain.auction.snapshot.entity.AuctionSnapshot;
import com.rocketcrew.pocat.domain.auction.snapshot.repository.AuctionSnapshotRepository;
import com.rocketcrew.pocat.global.exception.common.ErrorCode;
import com.rocketcrew.pocat.global.exception.domain.AuctionException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class AuctionSnapshotCommandService {

    private static final String AUCTION_SNAPSHOT_AUCTION_ID_UNIQUE_CONSTRAINT =
            "uk_auction_snapshots_auction_id";
    private static final int MYSQL_DUPLICATE_KEY_ERROR_CODE = 1062;

    private final AuctionSnapshotRepository auctionSnapshotRepository;
    private final AuctionRepository auctionRepository;
    private final ObjectMapper objectMapper;

    public void createSnapshot(Long auctionId, Long finalPrice) {
        if (auctionSnapshotRepository.findByAuctionId(auctionId).isPresent()) {
            return;
        }

        Auction auction = auctionRepository.findById(auctionId)
                .orElseThrow(() -> new AuctionException(ErrorCode.AUCTION_NOT_FOUND));

        AuctionSnapshot snapshot = AuctionSnapshot.builder()
                .auctionId(auction.getId())
                .finalPrice(finalPrice)
                .snapshotJson(buildSnapshotJson(auction, finalPrice))
                .build();

        try {
            auctionSnapshotRepository.saveAndFlush(snapshot);
        } catch (DataIntegrityViolationException e) {
            if (isDuplicateAuctionSnapshot(e)) {
                log.debug("Auction snapshot already exists. auctionId={}", auctionId);
                return;
            }
            throw e;
        }
    }

    private String buildSnapshotJson(Auction auction, Long finalPrice) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("auctionId", auction.getId());
        data.put("cardId", auction.getCardId());
        data.put("sellerId", auction.getSellerId());
        data.put("winnerId", auction.getHighestBidderId());
        data.put("title", auction.getTitle());
        data.put("description", auction.getDescription());
        data.put("startingPrice", auction.getStartingPrice());
        data.put("buyoutPrice", auction.getBuyoutPrice());
        data.put("finalPrice", finalPrice);
        data.put("status", auction.getStatus().name());
        data.put("startedAt", auction.getStartedAt());
        data.put("endedAt", auction.getEndedAt());
        try {
            return objectMapper.writeValueAsString(data);
        } catch (JsonProcessingException e) {
            log.error("Auction snapshot JSON serialization failed. auctionId={}", auction.getId(), e);
            throw new IllegalStateException("Auction snapshot JSON serialization failed", e);
        }
    }

    private boolean isDuplicateAuctionSnapshot(DataIntegrityViolationException exception) {
        Throwable current = exception;
        while (current != null) {
            if (isAuctionSnapshotDuplicateKey(current)) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private boolean isAuctionSnapshotDuplicateKey(Throwable throwable) {
        // MySQL 중복 키 오류 코드(1062)와 예외 메시지의 제약 조건명을 기준으로 판단한다.
        // DB 벤더를 변경하면 오류 코드와 메시지 형식이 달라질 수 있으므로 이 로직도 함께 수정해야 한다.
        if (throwable instanceof SQLException sqlException) {
            String message = sqlException.getMessage();
            return sqlException.getErrorCode() == MYSQL_DUPLICATE_KEY_ERROR_CODE
                    && message != null
                    && message.contains(AUCTION_SNAPSHOT_AUCTION_ID_UNIQUE_CONSTRAINT);
        }

        String message = throwable.getMessage();
        return message != null
                && message.contains(String.valueOf(MYSQL_DUPLICATE_KEY_ERROR_CODE))
                && message.contains(AUCTION_SNAPSHOT_AUCTION_ID_UNIQUE_CONSTRAINT);
    }
}
