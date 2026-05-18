package com.rocketcrew.pocat.global.exception.domain;

import com.rocketcrew.pocat.global.exception.common.ErrorCode;
import com.rocketcrew.pocat.global.exception.common.ServiceException;

public class SettlementException extends ServiceException {

    public SettlementException(ErrorCode errorCode) {
        super(errorCode);
    }
}
