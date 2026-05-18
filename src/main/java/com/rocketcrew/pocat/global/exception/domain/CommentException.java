package com.rocketcrew.pocat.global.exception.domain;

import com.rocketcrew.pocat.global.exception.common.ErrorCode;
import com.rocketcrew.pocat.global.exception.common.ServiceException;

public class CommentException extends ServiceException {
    public CommentException(ErrorCode errorCode) {
        super(errorCode);
    }
}
