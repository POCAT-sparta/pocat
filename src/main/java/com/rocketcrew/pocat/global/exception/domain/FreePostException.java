package com.rocketcrew.pocat.global.exception.domain;

import com.rocketcrew.pocat.global.exception.common.ErrorCode;
import com.rocketcrew.pocat.global.exception.common.ServiceException;

public class FreePostException extends ServiceException {
    public FreePostException(ErrorCode errorCode) {
        super(errorCode);
    }
}
