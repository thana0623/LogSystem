package com.logsys.service.impl;

import com.logsys.service.HealthService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class HealthServiceImpl implements HealthService {

    private final DataSource clickHouseDataSource;
    private final DataSource postgresDataSource;

    @Override
    public Map<String, String> check() {
        Map<String, String> result = new LinkedHashMap<>();
        result.put("timestamp", Instant.now().toString());

        String chStatus = pingClickHouse();
        String pgStatus = pingPostgres();

        result.put("clickhouse", chStatus);
        result.put("postgres", pgStatus);
        result.put("status", "UP".equals(chStatus) && "UP".equals(pgStatus) ? "UP" : "DOWN");

        return result;
    }

    private String pingClickHouse() {
        try {
            JdbcTemplate jdbc = new JdbcTemplate(clickHouseDataSource);
            jdbc.queryForObject("SELECT 1", Integer.class);
            return "UP";
        } catch (Exception e) {
            log.warn("ClickHouse health check failed: {}", e.getMessage());
            return "DOWN";
        }
    }

    private String pingPostgres() {
        try {
            JdbcTemplate jdbc = new JdbcTemplate(postgresDataSource);
            jdbc.queryForObject("SELECT 1", Integer.class);
            return "UP";
        } catch (Exception e) {
            log.warn("PostgreSQL health check failed: {}", e.getMessage());
            return "DOWN";
        }
    }
}
