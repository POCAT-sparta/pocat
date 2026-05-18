package com.rocketcrew.pocat.global.exception.domain;

import com.rocketcrew.pocat.global.exception.common.ErrorCode;
import com.rocketcrew.pocat.global.exception.common.ServiceException;

public class LikeException extends ServiceException {

    public LikeException(ErrorCode errorCode) {
        super(errorCode);
    }
}
