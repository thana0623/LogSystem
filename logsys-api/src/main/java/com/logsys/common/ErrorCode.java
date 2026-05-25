package com.logsys.common;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum ErrorCode {

    BAD_REQUEST("BAD_REQUEST", 400),
    VALIDATION_ERROR("VALIDATION_ERROR", 400),
    TIME_RANGE_TOO_LARGE("TIME_RANGE_TOO_LARGE", 400),
    NOT_FOUND("NOT_FOUND", 404),
    CONFLICT("CONFLICT", 409),
    INTERNAL_ERROR("INTERNAL_ERROR", 500),
    CLICKHOUSE_UNAVAILABLE("CLICKHOUSE_UNAVAILABLE", 503);

    private final String code;
    private final int httpStatus;
}
