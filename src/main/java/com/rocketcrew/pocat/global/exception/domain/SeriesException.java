package com.rocketcrew.pocat.global.exception.domain;

import com.rocketcrew.pocat.global.exception.common.ErrorCode;
import com.rocketcrew.pocat.global.exception.common.ServiceException;

public class SeriesException extends ServiceException {
    public SeriesException(ErrorCode errorCode) {
        super(errorCode);
    }
}
