package com.rocketcrew.pocat.global.exception.domain;

import com.rocketcrew.pocat.global.exception.common.ErrorCode;
import com.rocketcrew.pocat.global.exception.common.ServiceException;

public class NotificationException extends ServiceException {

    public NotificationException(ErrorCode errorCode) {
        super(errorCode);
    }
}
