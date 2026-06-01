package com.rocketcrew.pocat.global.exception.domain;

import com.rocketcrew.pocat.global.exception.common.ErrorCode;

public class InvalidAuctionEventPayloadException extends AuctionException {

    public InvalidAuctionEventPayloadException() {
        super(ErrorCode.AUCTION_EVENT_INVALID_PAYLOAD);
    }

    public InvalidAuctionEventPayloadException(Throwable cause) {
        super(ErrorCode.AUCTION_EVENT_INVALID_PAYLOAD, cause);
    }
}
