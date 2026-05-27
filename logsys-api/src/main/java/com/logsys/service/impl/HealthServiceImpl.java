package com.logsys.service.impl;

import com.logsys.service.HealthService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

@Slf4j
@Service
public class HealthServiceImpl implements HealthService {

    private final JdbcTemplate clickHouseJdbc;
    private final JdbcTemplate postgresJdbc;

    public HealthServiceImpl(DataSource clickHouseDataSource, DataSource postgresDataSource) {
        this.clickHouseJdbc = new JdbcTemplate(clickHouseDataSource);
        this.postgresJdbc = new JdbcTemplate(postgresDataSource);
    }

    @Override
    public Map<String, String> check() {
        Map<String, String> result = new LinkedHashMap<>();
        result.put("timestamp", Instant.now().toString());

        String chStatus = ping(clickHouseJdbc, "ClickHouse");
        String pgStatus = ping(postgresJdbc, "PostgreSQL");

        result.put("clickhouse", chStatus);
        result.put("postgres", pgStatus);
        result.put("status", "UP".equals(chStatus) && "UP".equals(pgStatus) ? "UP" : "DOWN");

        return result;
    }

    private String ping(JdbcTemplate jdbc, String name) {
        try {
            jdbc.queryForObject("SELECT 1", Integer.class);
            return "UP";
        } catch (Exception e) {
            log.warn("{} health check failed: {}", name, e.getMessage());
            return "DOWN";
        }
    }
}
