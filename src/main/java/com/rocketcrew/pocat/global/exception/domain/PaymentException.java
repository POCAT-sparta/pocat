package com.rocketcrew.pocat.global.exception.domain;

import com.rocketcrew.pocat.global.exception.common.ErrorCode;
import com.rocketcrew.pocat.global.exception.common.ServiceException;

public class PaymentException extends ServiceException {

    public PaymentException(ErrorCode errorCode) {
        super(errorCode);
    }
}
