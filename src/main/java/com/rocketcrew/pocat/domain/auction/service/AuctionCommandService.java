package com.rocketcrew.pocat.domain.auction.service;

import com.rocketcrew.pocat.domain.auction.dto.request.CreateAuctionRequest;
import com.rocketcrew.pocat.domain.auction.dto.request.InspectAuctionRequest;
import com.rocketcrew.pocat.domain.auction.dto.request.UpdateAuctionRequest;
import com.rocketcrew.pocat.domain.auction.dto.response.CancelAuctionResponse;
import com.rocketcrew.pocat.domain.auction.dto.response.CreateAuctionResponse;
import com.rocketcrew.pocat.domain.auction.dto.response.InspectAuctionResponse;
import com.rocketcrew.pocat.domain.auction.dto.response.UpdateAuctionResponse;
import com.rocketcrew.pocat.domain.auction.entity.Auction;
import com.rocketcrew.pocat.domain.auction.enums.AuctionInspectionResult;
import com.rocketcrew.pocat.domain.auction.enums.AuctionStatus;
import com.rocketcrew.pocat.domain.auction.repository.AuctionRepository;
import com.rocketcrew.pocat.domain.card.entity.Card;
import com.rocketcrew.pocat.domain.card.service.CardQueryService;
import com.rocketcrew.pocat.domain.user.service.UserQueryService;
import com.rocketcrew.pocat.global.exception.common.ErrorCode;
import com.rocketcrew.pocat.global.exception.domain.AuctionException;
import com.rocketcrew.pocat.global.exception.domain.CardException;
import com.rocketcrew.pocat.global.exception.domain.UserException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Transactional
public class AuctionCommandService {

    private final AuctionRepository auctionRepository;
    private final CardQueryService cardQueryService;
    private final UserQueryService userQueryService;

    public CreateAuctionResponse createAuction(Long sellerId, CreateAuctionRequest request) {
        Card card = cardQueryService.validateRegistrableForAuction(request.cardId());
        validateBuyoutPrice(request.startingPrice(), request.buyoutPrice());
        Auction auction = Auction.builder()
                .cardId(request.cardId())
                .sellerId(sellerId)
                .title(request.title())
                .description(request.description())
                .cardImageUrl(card.getImageUrl())
                .startingPrice(request.startingPrice())
                .buyoutPrice(request.buyoutPrice())
                .status(AuctionStatus.PENDING)
                .build();
        return CreateAuctionResponse.from(auctionRepository.save(auction));
    }

    public UpdateAuctionResponse updateAuction(Long sellerId, Long id, UpdateAuctionRequest request) {
        Auction auction = auctionRepository.findById(id)
                .orElseThrow(() -> new AuctionException(ErrorCode.AUCTION_NOT_FOUND));
        validateSeller(auction, sellerId);
        validatePending(auction);
        validateUpdateRequest(request);
        Long startingPrice = request.startingPrice() != null ? request.startingPrice() : auction.getStartingPrice();
        Long buyoutPrice = request.buyoutPrice() != null ? request.buyoutPrice() : auction.getBuyoutPrice();
        validateBuyoutPrice(startingPrice, buyoutPrice);
        auction.update(request.title(), request.description(), request.startingPrice(), request.buyoutPrice());
        return UpdateAuctionResponse.from(auction);
    }

    public CancelAuctionResponse cancelAuction(Long sellerId, Long id) {
        Auction auction = auctionRepository.findById(id)
                .orElseThrow(() -> new AuctionException(ErrorCode.AUCTION_NOT_FOUND));
        validateSeller(auction, sellerId);
        validatePending(auction);
        auction.cancel(null);
        return CancelAuctionResponse.from(auction);
    }

    public InspectAuctionResponse inspectAuction(Long adminId, Long id, InspectAuctionRequest request) {
        Auction auction = auctionRepository.findById(id)
                .orElseThrow(() -> new AuctionException(ErrorCode.AUCTION_NOT_FOUND));
        validateInspectable(auction);

        if (request.result() == AuctionInspectionResult.PASSED) {
            validateAuctionDataForInspection(auction);
            auction.approve(adminId, LocalDateTime.now());
            return InspectAuctionResponse.from(auction);
        }

        validateRejectReason(request.reason());
        auction.reject(request.reason().trim());
        return InspectAuctionResponse.from(auction);
    }

    private void validateSeller(Auction auction, Long sellerId) {
        if (!auction.getSellerId().equals(sellerId)) {
            throw new AuctionException(ErrorCode.USER_FORBIDDEN);
        }
    }

    private void validatePending(Auction auction) {
        if (auction.getStatus() != AuctionStatus.PENDING) {
            throw new AuctionException(ErrorCode.AUCTION_NOT_PENDING);
        }
    }


    private void validateInspectable(Auction auction) {
        if (auction.getStatus() != AuctionStatus.PENDING
                && auction.getStatus() != AuctionStatus.INSPECTING) {
            throw new AuctionException(ErrorCode.AUCTION_NOT_INSPECTING);
        }
    }

    private void validateAuctionDataForInspection(Auction auction) {
        validateSellerExists(auction.getSellerId());
        validateCardRegistrable(auction.getCardId());
        validateTitle(auction.getTitle());
        validateStartingPrice(auction.getStartingPrice());
        validateBuyoutPrice(auction.getStartingPrice(), auction.getBuyoutPrice());
    }

    private void validateSellerExists(Long sellerId) {
        try {
            userQueryService.getUserEntity(sellerId);
        } catch (UserException e) {
            throw new AuctionException(ErrorCode.AUCTION_SELLER_NOT_FOUND);
        }
    }

    private void validateCardRegistrable(Long cardId) {
        try {
            cardQueryService.validateRegistrableForAuction(cardId);
        } catch (CardException e) {
            if (e.getErrorCode() == ErrorCode.CARD_NOT_ACTIVE) {
                throw new AuctionException(ErrorCode.AUCTION_CARD_NOT_ACTIVE);
            }
            throw new AuctionException(ErrorCode.AUCTION_CARD_NOT_FOUND);
        }
    }

    private void validateTitle(String title) {
        if (!StringUtils.hasText(title) || title.length() > 255) {
            throw new AuctionException(ErrorCode.INVALID_INPUT);
        }
    }

    private void validateStartingPrice(Long startingPrice) {
        if (startingPrice == null || startingPrice <= 0) {
            throw new AuctionException(ErrorCode.AUCTION_PRICE_INVALID);
        }
    }

    private void validateRejectReason(String reason) {
        if (!StringUtils.hasText(reason)) {
            throw new AuctionException(ErrorCode.AUCTION_REASON_REQUIRED);
        }
    }

    private void validateUpdateRequest(UpdateAuctionRequest request) {
        if (request.title() == null
                && request.description() == null
                && request.startingPrice() == null
                && request.buyoutPrice() == null) {
            throw new AuctionException(ErrorCode.AUCTION_UPDATE_EMPTY);
        }
    }

    private void validateBuyoutPrice(Long startingPrice, Long buyoutPrice) {
        if (startingPrice == null || buyoutPrice == null) {
            return;
        }
        if (buyoutPrice <= startingPrice) {
            throw new AuctionException(ErrorCode.AUCTION_PRICE_INVALID);
        }
    }
}
