package com.logsys.model.vo;

import lombok.Data;

import java.time.Instant;
import java.util.Map;

@Data
public class LogEntryVO {

    private Instant timestamp;
    private String serviceName;
    private String serviceInstance;
    private String sourceHost;
    private String sourceType;
    private String level;
    private String logger;
    private String message;
    private String traceId;
    private String spanId;
    private String exceptionType;
    private String exceptionMessage;
    private Map<String, String> tags;
    private Map<String, String> fields;
}
