package com.logsys.service.impl;

import com.logsys.common.RangeParser;
import com.logsys.dao.clickhouse.ErrorClusterMapper;
import com.logsys.model.vo.ErrorClusterVO;
import com.logsys.service.ErrorClusterService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class ErrorClusterServiceImpl implements ErrorClusterService {

    private final ErrorClusterMapper errorClusterMapper;

    @Override
    public List<ErrorClusterVO> top(String range, int limit, String serviceName, String trend) {
        int safeLimit = Math.min(Math.max(limit, 1), 50);
        Instant endTime = Instant.now();
        Instant startTime = endTime.minus(RangeParser.parse(range));

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
        Instant startTime = endTime.minus(RangeParser.parse(range));

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
}
