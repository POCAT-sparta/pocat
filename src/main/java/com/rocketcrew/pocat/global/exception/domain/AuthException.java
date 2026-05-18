package com.rocketcrew.pocat.global.exception.domain;

import com.rocketcrew.pocat.global.exception.common.ErrorCode;
import com.rocketcrew.pocat.global.exception.common.ServiceException;

public class AuthException extends ServiceException {
    public AuthException(ErrorCode errorCode) {
        super(errorCode);
    }
}
