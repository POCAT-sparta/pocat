package com.rocketcrew.pocat.global.exception.domain;

import com.rocketcrew.pocat.global.exception.common.ErrorCode;
import com.rocketcrew.pocat.global.exception.common.ServiceException;

public class ChatException extends ServiceException {

    public ChatException(ErrorCode errorCode) {
        super(errorCode);
    }
}
