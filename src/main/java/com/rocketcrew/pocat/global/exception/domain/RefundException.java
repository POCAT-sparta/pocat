package com.rocketcrew.pocat.global.exception.domain;

import com.rocketcrew.pocat.global.exception.common.ErrorCode;
import com.rocketcrew.pocat.global.exception.common.ServiceException;

public class RefundException extends ServiceException {

    public RefundException(ErrorCode errorCode) {
        super(errorCode);
    }
}
