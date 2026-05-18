package com.rocketcrew.pocat.global.exception.domain;

import com.rocketcrew.pocat.global.exception.common.ErrorCode;
import com.rocketcrew.pocat.global.exception.common.ServiceException;

public class OrderException extends ServiceException {

    public OrderException(ErrorCode errorCode) {
        super(errorCode);
    }
}
