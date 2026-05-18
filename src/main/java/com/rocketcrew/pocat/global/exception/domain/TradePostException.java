package com.rocketcrew.pocat.global.exception.domain;

import com.rocketcrew.pocat.global.exception.common.ErrorCode;
import com.rocketcrew.pocat.global.exception.common.ServiceException;

public class TradePostException extends ServiceException {
    public TradePostException(ErrorCode errorCode) {
        super(errorCode);
    }
}
