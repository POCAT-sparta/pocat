package com.rocketcrew.pocat.global.exception.domain;

import com.rocketcrew.pocat.global.exception.common.ErrorCode;
import com.rocketcrew.pocat.global.exception.common.ServiceException;

public class UserException extends ServiceException {
    public UserException(ErrorCode errorCode) {
        super(errorCode);
    }
}
