package com.rocketcrew.pocat.global.exception.domain;

import com.rocketcrew.pocat.global.exception.common.ErrorCode;
import com.rocketcrew.pocat.global.exception.common.ServiceException;

public class BidException extends ServiceException {

    public BidException(ErrorCode errorCode) {
        super(errorCode);
    }

    public BidException(ErrorCode errorCode, Throwable cause) {
        super(errorCode, cause);
    }
}
