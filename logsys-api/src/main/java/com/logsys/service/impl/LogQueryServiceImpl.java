package com.logsys.service.impl;

import com.logsys.common.BizException;
import com.logsys.common.ErrorCode;
import com.logsys.dao.clickhouse.LogMapper;
import com.logsys.model.dto.LogQueryRequest;
import com.logsys.model.vo.LogEntryVO;
import com.logsys.model.vo.PageResult;
import com.logsys.service.LogQueryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class LogQueryServiceImpl implements LogQueryService {

    private static final Set<String> VALID_LEVELS = Set.of(
            "TRACE", "DEBUG", "INFO", "WARN", "ERROR", "FATAL");
    private static final long MAX_RANGE_DAYS = 7;

    private final LogMapper logMapper;

    @Override
    public PageResult<LogEntryVO> query(LogQueryRequest request) {
        // Normalize pagination
        int page = request.getPage() != null && request.getPage() > 0 ? request.getPage() : 1;
        int pageSize = request.getPageSize() != null && request.getPageSize() > 0
                ? Math.min(request.getPageSize(), 200) : 50;

        // Validate time range
        Instant startTime = parseTime(request.getStartTime(), "start_time");
        Instant endTime = parseTime(request.getEndTime(), "end_time");

        if (!endTime.isAfter(startTime)) {
            throw new BizException(ErrorCode.BAD_REQUEST, "end_time must be after start_time");
        }

        long days = Duration.between(startTime, endTime).toDays();
        if (days > MAX_RANGE_DAYS) {
            throw new BizException(ErrorCode.TIME_RANGE_TOO_LARGE, "Time range must not exceed 7 days");
        }

        // Validate keyword
        String keyword = request.getKeyword();
        if (keyword != null && !keyword.isBlank() && keyword.length() < 2) {
            throw new BizException(ErrorCode.BAD_REQUEST, "keyword must be at least 2 characters");
        }

        // Normalize level
        String level = request.getLevel();
        if (level != null && !VALID_LEVELS.contains(level.toUpperCase())) {
            level = null; // invalid level → ignore
        }

        // Build params
        Map<String, Object> params = new HashMap<>();
        params.put("serviceName", request.getServiceName());
        params.put("level", level != null ? level.toUpperCase() : null);
        params.put("keyword", keyword);
        params.put("traceId", request.getTraceId());
        params.put("startTime", startTime.toString());
        params.put("endTime", endTime.toString());
        params.put("sort", "asc".equalsIgnoreCase(request.getSort()) ? "ASC" : "DESC");
        params.put("offset", (long) (page - 1) * pageSize);
        params.put("limit", (long) pageSize);

        long total = logMapper.countLogs(params);
        List<LogEntryVO> items = total > 0 ? logMapper.queryLogs(params) : List.of();

        return new PageResult<>(total, page, pageSize, items);
    }

    private Instant parseTime(String timeStr, String fieldName) {
        if (timeStr == null || timeStr.isBlank()) {
            throw new BizException(ErrorCode.BAD_REQUEST, fieldName + " is required");
        }
        try {
            return Instant.parse(timeStr);
        } catch (DateTimeParseException e) {
            throw new BizException(ErrorCode.BAD_REQUEST,
                    fieldName + " must be ISO 8601 UTC format, e.g. 2026-05-24T12:00:00Z");
        }
    }
}
