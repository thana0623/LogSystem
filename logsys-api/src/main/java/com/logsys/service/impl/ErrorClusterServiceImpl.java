package com.logsys.service.impl;

import com.logsys.common.BizException;
import com.logsys.common.ErrorCode;
import com.logsys.dao.clickhouse.ErrorClusterMapper;
import com.logsys.model.vo.ErrorClusterVO;
import com.logsys.service.ErrorClusterService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class ErrorClusterServiceImpl implements ErrorClusterService {

    private static final Map<String, Duration> RANGE_MAP = Map.of(
            "1h", Duration.ofHours(1),
            "6h", Duration.ofHours(6),
            "24h", Duration.ofHours(24),
            "7d", Duration.ofDays(7),
            "30d", Duration.ofDays(30));

    private final ErrorClusterMapper errorClusterMapper;

    @Override
    public List<ErrorClusterVO> top(String range, int limit, String serviceName, String trend) {
        int safeLimit = Math.min(Math.max(limit, 1), 50);
        Instant endTime = Instant.now();
        Instant startTime = endTime.minus(parseRange(range));

        Map<String, Object> params = new HashMap<>();
        params.put("startTime", startTime.toString());
        params.put("endTime", endTime.toString());
        params.put("limit", safeLimit);
        params.put("serviceName", serviceName);

        return errorClusterMapper.queryTopErrors(params);
    }

    @Override
    public Map<String, Object> clusters(String range, String serviceName) {
        Instant endTime = Instant.now();
        Instant startTime = endTime.minus(parseRange(range));

        Map<String, Object> params = new HashMap<>();
        params.put("startTime", startTime.toString());
        params.put("endTime", endTime.toString());
        params.put("serviceName", serviceName);

        Map<String, Object> summary = errorClusterMapper.queryClusterSummary(params);
        List<Map<String, Object>> topClusters = errorClusterMapper.queryClusters(params);

        Map<String, Object> result = new HashMap<>();
        result.put("summary", summary != null ? summary : Map.of(
                "total_clusters", 0,
                "total_errors", 0,
                "new_clusters_24h", 0,
                "resolved_clusters_24h", 0));
        result.put("top_clusters", topClusters);
        return result;
    }

    private Duration parseRange(String range) {
        if (range == null) return Duration.ofHours(24);
        Duration d = RANGE_MAP.get(range);
        if (d == null) throw new BizException(ErrorCode.BAD_REQUEST, "Invalid range: " + range);
        return d;
    }
}
