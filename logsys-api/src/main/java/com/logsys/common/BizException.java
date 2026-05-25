package com.logsys.common;

import lombok.Getter;

@Getter
public class BizException extends RuntimeException {

    private final ErrorCode errorCode;

    public BizException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public static BizException notFound(String resource, String id) {
        return new BizException(ErrorCode.NOT_FOUND,
                String.format("%s '%s' not found", resource, id));
    }

    public static BizException conflict(String resource, String id) {
        return new BizException(ErrorCode.CONFLICT,
                String.format("%s '%s' already exists", resource, id));
    }
}
