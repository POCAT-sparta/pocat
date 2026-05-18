package com.rocketcrew.pocat.global.exception.domain;

import com.rocketcrew.pocat.global.exception.common.ErrorCode;
import com.rocketcrew.pocat.global.exception.common.ServiceException;

public class AuctionException extends ServiceException {

    public AuctionException(ErrorCode errorCode) {
        super(errorCode);
    }
}
