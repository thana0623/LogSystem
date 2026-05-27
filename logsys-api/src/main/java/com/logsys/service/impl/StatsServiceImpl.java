package com.logsys.service.impl;

import com.logsys.common.RangeParser;
import com.logsys.dao.postgres.ServiceMapper;
import com.logsys.service.StatsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class StatsServiceImpl implements StatsService {

    private final ServiceMapper serviceMapper;

    @Override
    public Map<String, Object> overview(String range) {
        // TODO: implement ClickHouse queries for total_logs, total_errors, etc.
        int serviceCount = serviceMapper.count();

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
}
