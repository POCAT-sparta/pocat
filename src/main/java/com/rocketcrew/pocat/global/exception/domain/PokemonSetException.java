package com.rocketcrew.pocat.global.exception.domain;

import com.rocketcrew.pocat.global.exception.common.ErrorCode;
import com.rocketcrew.pocat.global.exception.common.ServiceException;

public class PokemonSetException extends ServiceException {
    public PokemonSetException(ErrorCode errorCode) {
        super(errorCode);
    }
}
