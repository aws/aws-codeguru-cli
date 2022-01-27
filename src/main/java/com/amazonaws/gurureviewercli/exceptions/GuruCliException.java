package com.amazonaws.gurureviewercli.exceptions;

import lombok.Getter;

import com.amazonaws.gurureviewercli.model.ErrorCodes;

public class GuruCliException extends RuntimeException {

    public GuruCliException(final ErrorCodes errorCode) {
        this.errorCode = errorCode;
    }

    public GuruCliException(final ErrorCodes errorCode, final String msg) {
        super(msg);
        this.errorCode = errorCode;
    }

    public GuruCliException(final ErrorCodes errorCode, final String msg, final Throwable cause) {
        super(msg, cause);
        this.errorCode = errorCode;
    }

    @Getter
    private ErrorCodes errorCode;

}
