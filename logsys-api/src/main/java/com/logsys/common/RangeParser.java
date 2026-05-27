package com.logsys.common;

import java.time.Duration;
import java.util.Map;

public final class RangeParser {

    private static final Map<String, Duration> RANGE_MAP = Map.of(
            "1h", Duration.ofHours(1),
            "6h", Duration.ofHours(6),
            "24h", Duration.ofHours(24),
            "7d", Duration.ofDays(7),
            "30d", Duration.ofDays(30));

    private RangeParser() {}

    public static Duration parse(String range) {
        if (range == null) return Duration.ofHours(24);
        Duration d = RANGE_MAP.get(range);
        if (d == null) throw new BizException(ErrorCode.BAD_REQUEST, "Invalid range: " + range);
        return d;
    }
}
