package com.logsys.service.impl;

import com.logsys.common.BizException;
import com.logsys.common.ErrorCode;
import com.logsys.dao.clickhouse.LogMapper;
import com.logsys.dao.postgres.ServiceMapper;
import com.logsys.service.StatsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class StatsServiceImpl implements StatsService {

    private static final Map<String, Duration> RANGE_MAP = Map.of(
            "1h", Duration.ofHours(1),
            "6h", Duration.ofHours(6),
            "24h", Duration.ofHours(24),
            "7d", Duration.ofDays(7));

    private final ServiceMapper serviceMapper;

    @Override
    public Map<String, Object> overview(String range) {
        Instant endTime = Instant.now();
        Instant startTime = endTime.minus(parseRange(range));

        Map<String, Object> params = new HashMap<>();
        params.put("startTime", startTime.toString());
        params.put("endTime", endTime.toString());

        int serviceCount = serviceMapper.findAll().size();

        Map<String, Object> result = new HashMap<>();
        result.put("total_logs", 0);
        result.put("total_errors", 0);
        result.put("error_rate", 0.0);
        result.put("service_count", serviceCount);
        result.put("active_services", 0);
        result.put("silent_services", 0);
        result.put("log_volume_trend", java.util.List.of());
        result.put("level_distribution", Map.of());
        result.put("top_services_by_volume", java.util.List.of());
        result.put("top_errors", java.util.List.of());

        return result;
    }

    private Duration parseRange(String range) {
        if (range == null) return Duration.ofHours(24);
        Duration d = RANGE_MAP.get(range);
        if (d == null) throw new BizException(ErrorCode.BAD_REQUEST, "Invalid range: " + range);
        return d;
    }
}
