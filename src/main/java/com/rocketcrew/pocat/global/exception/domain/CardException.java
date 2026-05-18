package com.rocketcrew.pocat.global.exception.domain;

import com.rocketcrew.pocat.global.exception.common.ErrorCode;
import com.rocketcrew.pocat.global.exception.common.ServiceException;

public class CardException extends ServiceException {
    public CardException(ErrorCode errorCode) {
        super(errorCode);
    }
}
