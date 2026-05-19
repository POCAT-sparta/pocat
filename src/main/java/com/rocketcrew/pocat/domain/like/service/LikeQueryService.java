package com.rocketcrew.pocat.domain.like.service;

import com.rocketcrew.pocat.domain.auction.entity.Auction;
import com.rocketcrew.pocat.domain.auction.repository.AuctionRepository;
import com.rocketcrew.pocat.domain.card.entity.Card;
import com.rocketcrew.pocat.domain.card.repository.CardRepository;
import com.rocketcrew.pocat.domain.like.dto.response.LikeResponse;
import com.rocketcrew.pocat.domain.like.entity.Like;
import com.rocketcrew.pocat.domain.like.repository.LikeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class LikeQueryService {

    private final LikeRepository likeRepository;
    private final AuctionRepository auctionRepository;
    private final CardRepository cardRepository;

    public Page<LikeResponse> getMyLikes(Long userId, Pageable pageable) {
        Page<Like> likes = likeRepository.findByUserId(userId, pageable);

        List<Long> auctionIds = likes.getContent().stream()
                .map(Like::getAuctionId)
                .distinct()
                .toList();

        Map<Long, Auction> auctionMap = auctionRepository.findAllById(auctionIds)
                .stream()
                .collect(Collectors.toMap(Auction::getId, a -> a));

        List<Long> cardIds = auctionMap.values().stream()
                .map(Auction::getCardId)
                .distinct()
                .toList();

        Map<Long, Card> cardMap = cardRepository.findAllById(cardIds)
                .stream()
                .collect(Collectors.toMap(Card::getId, c -> c));

        return likes.map(like -> {
            Auction auction = auctionMap.get(like.getAuctionId());
            if (auction == null) {
                return new LikeResponse(like.getId(), like.getAuctionId(), null, null, null, null, null, null, null, like.getCreatedAt());
            }
            Card card = cardMap.get(auction.getCardId());
            return new LikeResponse(
                    like.getId(),
                    like.getAuctionId(),
                    auction.getTitle(),
                    card != null ? card.getName() : null,
                    card != null ? card.getGrade().name() : null,
                    auction.getCardImageUrl(),
                    auction.getHighestPrice(),
                    auction.getEndedAt(),
                    auction.getStatus().name(),
                    like.getCreatedAt()
            );
        });
    }
}
